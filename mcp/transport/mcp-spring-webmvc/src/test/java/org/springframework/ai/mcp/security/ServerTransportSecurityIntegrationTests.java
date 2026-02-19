/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.security;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.TestUtil;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.mcp.server.TomcatTestUtil;
import org.springframework.ai.mcp.server.TomcatTestUtil.TomcatServer;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test the header security validation for all transport types.
 *
 * @author Daniel Garnier-Moiroux
 */
@ParameterizedClass
@MethodSource("transports")
public class ServerTransportSecurityIntegrationTests {

	private static final String DISALLOWED_ORIGIN = "https://malicious.example.com";

	private static final String DISALLOWED_HOST = "malicious.example.com:8080";

	@Parameter
	private static Class<?> configClass;

	private static TomcatServer tomcatServer;

	private static String baseUrl;

	@BeforeParameterizedClassInvocation
	static void createTransportAndStartTomcat(Class<?> configClass) {
		var port = TestUtil.findAvailablePort();
		baseUrl = "http://localhost:" + port;
		startTomcat(configClass, port);
	}

	@AfterAll
	static void afterAll() {
		stopTomcat();
	}

	private McpSyncClient mcpClient;

	private TestRequestCustomizer requestCustomizer;

	@BeforeEach
	void setUp() {
		this.mcpClient = tomcatServer.appContext().getBean(McpSyncClient.class);
		this.requestCustomizer = tomcatServer.appContext().getBean(TestRequestCustomizer.class);
		this.requestCustomizer.reset();
	}

	@AfterEach
	void tearDown() {
		this.mcpClient.close();
	}

	@Test
	void originAllowed() {
		this.requestCustomizer.setOriginHeader(baseUrl);
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void noOrigin() {
		this.requestCustomizer.setOriginHeader(null);
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void connectOriginNotAllowed() {
		this.requestCustomizer.setOriginHeader(DISALLOWED_ORIGIN);
		assertThatThrownBy(() -> this.mcpClient.initialize());
	}

	@Test
	void messageOriginNotAllowed() {
		this.requestCustomizer.setOriginHeader(baseUrl);
		this.mcpClient.initialize();
		this.requestCustomizer.setOriginHeader(DISALLOWED_ORIGIN);
		assertThatThrownBy(() -> this.mcpClient.listTools());
	}

	@Test
	void hostAllowed() {
		// Host header is set by default by HttpClient to the request URI host
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void connectHostNotAllowed() {
		this.requestCustomizer.setHostHeader(DISALLOWED_HOST);
		assertThatThrownBy(() -> this.mcpClient.initialize());
	}

	@Test
	void messageHostNotAllowed() {
		this.mcpClient.initialize();
		this.requestCustomizer.setHostHeader(DISALLOWED_HOST);
		assertThatThrownBy(() -> this.mcpClient.listTools());
	}

	// ----------------------------------------------------
	// Tomcat management
	// ----------------------------------------------------

	private static void startTomcat(Class<?> componentClass, int port) {
		tomcatServer = TomcatTestUtil.createTomcatServer("", port, componentClass);
		try {
			tomcatServer.tomcat().start();
			assertThat(tomcatServer.tomcat().getServer().getState()).isEqualTo(LifecycleState.STARTED);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}
	}

	private static void stopTomcat() {
		if (tomcatServer != null) {
			if (tomcatServer.appContext() != null) {
				tomcatServer.appContext().close();
			}
			if (tomcatServer.tomcat() != null) {
				try {
					tomcatServer.tomcat().stop();
					tomcatServer.tomcat().destroy();
				}
				catch (LifecycleException e) {
					throw new RuntimeException("Failed to stop Tomcat", e);
				}
			}
		}
	}

	// ----------------------------------------------------
	// Transport servers to test
	// ----------------------------------------------------

	/**
	 * All transport types we want to test. We use a {@link MethodSource} rather than a
	 * {@link org.junit.jupiter.params.provider.ValueSource} to provide a readable name.
	 */
	static Stream<Arguments> transports() {
		//@formatter:off
		return Stream.of(
				Arguments.arguments(Named.named("SSE", SseConfig.class)),
				Arguments.arguments(Named.named("Streamable HTTP", StreamableHttpConfig.class)),
				Arguments.arguments(Named.named("Stateless", StatelessConfig.class))
		);
		//@formatter:on
	}

	// ----------------------------------------------------
	// Spring Configuration classes
	// ----------------------------------------------------

	@Configuration
	static class CommonConfig {

		@Bean
		TestRequestCustomizer requestCustomizer() {
			return new TestRequestCustomizer();
		}

		@Bean
		DefaultServerTransportSecurityValidator validator() {
			return DefaultServerTransportSecurityValidator.builder()
				.allowedOrigin("http://localhost:*")
				.allowedHost("localhost:*")
				.build();
		}

	}

	@Configuration
	@EnableWebMvc
	@Import(CommonConfig.class)
	static class SseConfig {

		@Bean
		@Scope("prototype")
		McpSyncClient createMcpClient(McpSyncHttpClientRequestCustomizer requestCustomizer) {
			var transport = HttpClientSseClientTransport.builder(baseUrl)
				.httpRequestCustomizer(requestCustomizer)
				.jsonMapper(McpJsonDefaults.getMapper())
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Bean
		public WebMvcSseServerTransportProvider webMvcSseServerTransport(
				DefaultServerTransportSecurityValidator validator) {
			return WebMvcSseServerTransportProvider.builder()
				.messageEndpoint("/mcp/message")
				.securityValidator(validator)
				.build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

		@Bean
		public McpSyncServer mcpServer(WebMvcSseServerTransportProvider transportProvider) {
			return McpServer.sync(transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

	}

	@Configuration
	@EnableWebMvc
	@Import(CommonConfig.class)
	static class StreamableHttpConfig {

		@Bean
		@Scope("prototype")
		McpSyncClient createMcpClient(McpSyncHttpClientRequestCustomizer requestCustomizer) {
			var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
				.httpRequestCustomizer(requestCustomizer)
				.jsonMapper(McpJsonDefaults.getMapper())
				.openConnectionOnStartup(true)
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Bean
		public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransport(
				DefaultServerTransportSecurityValidator validator) {
			return WebMvcStreamableServerTransportProvider.builder().securityValidator(validator).build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(
				WebMvcStreamableServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

		@Bean
		public McpSyncServer mcpServer(WebMvcStreamableServerTransportProvider transportProvider) {
			return McpServer.sync(transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

	}

	@Configuration
	@EnableWebMvc
	@Import(CommonConfig.class)
	static class StatelessConfig {

		@Bean
		@Scope("prototype")
		McpSyncClient createMcpClient(McpSyncHttpClientRequestCustomizer requestCustomizer) {
			var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
				.httpRequestCustomizer(requestCustomizer)
				.jsonMapper(McpJsonDefaults.getMapper())
				.openConnectionOnStartup(true)
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Bean
		public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
				DefaultServerTransportSecurityValidator validator) {
			return WebMvcStatelessServerTransport.builder().securityValidator(validator).build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcStatelessServerTransport transportProvider) {
			return transportProvider.getRouterFunction();
		}

		@Bean
		public McpStatelessSyncServer mcpStatelessServer(WebMvcStatelessServerTransport transportProvider) {
			return McpServer.sync(transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

	}

	static class TestRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

		private String originHeader = null;

		private String hostHeader = null;

		@Override
		public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body,
				McpTransportContext context) {
			if (this.originHeader != null) {
				builder.header("Origin", this.originHeader);
			}
			if (this.hostHeader != null) {
				builder.header("Host", this.hostHeader);
			}
		}

		public void setOriginHeader(String originHeader) {
			this.originHeader = originHeader;
		}

		public void setHostHeader(String hostHeader) {
			this.hostHeader = hostHeader;
		}

		public void reset() {
			this.originHeader = null;
			this.hostHeader = null;
		}

	}

}
