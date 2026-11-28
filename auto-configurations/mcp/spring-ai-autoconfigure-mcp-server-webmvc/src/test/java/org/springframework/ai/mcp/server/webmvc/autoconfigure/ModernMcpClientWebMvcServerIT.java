/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.server.webmvc.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.transport.McpHttpRequestCustomizer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientModernAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connects the auto-configured modern MCP client to an auto-configured modern MCP server
 * on Spring MVC, over HTTP on an embedded Tomcat.
 *
 * @author Christian Tzolov
 */
class ModernMcpClientWebMvcServerIT {

	private static final String USER_HEADER = "X-Test-User";

	private static final String CONNECTIONS = "spring.ai.mcp.client.streamable-http.connections.";

	private final ApplicationContextRunner clientContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class,
				McpClientModernAutoConfiguration.class, McpToolCallbackAutoConfiguration.class))
		.withUserConfiguration(ClientConfiguration.class);

	private Tomcat tomcat;

	private AnnotationConfigWebApplicationContext serverContext;

	@BeforeEach
	void startServer() throws LifecycleException {
		this.tomcat = new Tomcat();
		this.tomcat.setPort(0);
		String baseDir = System.getProperty("java.io.tmpdir");
		this.tomcat.setBaseDir(baseDir);
		Context context = this.tomcat.addContext("", baseDir);

		this.serverContext = new AnnotationConfigWebApplicationContext();
		TestPropertyValues.of("spring.ai.mcp.server.protocol=MODERN").applyTo(this.serverContext);
		this.serverContext.register(ModernServerApplication.class);
		this.serverContext.setServletContext(context.getServletContext());
		this.serverContext.refresh();

		// The streaming answers of the transport need asynchronous request processing.
		Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(this.serverContext))
			.setAsyncSupported(true);
		context.addServletMappingDecoded("/*", "dispatcherServlet");

		this.tomcat.getConnector();
		this.tomcat.start();
	}

	@AfterEach
	void stopServer() throws LifecycleException {
		this.serverContext.close();
		this.tomcat.stop();
		this.tomcat.destroy();
	}

	@Test
	void modernClientListsAndCallsToolsOfAWebMvcServerOnBehalfOfTheCurrentUser() {
		this.clientContextRunner
			.withPropertyValues(
					CONNECTIONS + "greeter.url=http://localhost:" + this.tomcat.getConnector().getLocalPort(),
					CONNECTIONS + "greeter.protocol=MODERN")
			.run(clientContext -> {
				SeenUsers seenUsers = this.serverContext.getBean(SeenUsers.class);
				CurrentUser currentUser = clientContext.getBean(CurrentUser.class);
				ToolCallbackProvider provider = clientContext.getBean(ToolCallbackProvider.class);

				currentUser.name.set("alice");
				ToolCallback[] toolCallbacks = provider.getToolCallbacks();

				assertThat(toolCallbacks).hasSize(1);
				assertThat(toolCallbacks[0].getToolDefinition().name()).isEqualTo("greeter_greet");
				assertThat(toolCallbacks[0].getToolDefinition().description()).isEqualTo("Greets");
				assertThat(toolCallbacks[0].call("{\"name\":\"Spring\"}")).contains("Hello Spring");

				currentUser.name.set("bob");
				assertThat(provider.getToolCallbacks()[0].call("{\"name\":\"AI\"}")).contains("Hello AI");

				// tools/list and tools/call for alice, then the same for bob
				assertThat(seenUsers.names).containsExactly("alice", "alice", "bob", "bob");
			});
	}

	/** The user on whose behalf the client application currently acts. */
	static class CurrentUser {

		final AtomicReference<String> name = new AtomicReference<>("nobody");

	}

	/** The users the server saw, one entry for each request. */
	static class SeenUsers {

		final List<String> names = new CopyOnWriteArrayList<>();

	}

	@Configuration(proxyBeanMethods = false)
	static class ClientConfiguration {

		@Bean
		CurrentUser currentUser() {
			return new CurrentUser();
		}

		/**
		 * Supplies the identity of a call: the transport context is read on the thread
		 * that starts the call.
		 */
		@Bean
		McpClientCustomizer<McpClient.SyncBuilder> identityCustomizer(CurrentUser currentUser) {
			return (name, builder) -> builder
				.transportContextProvider(() -> McpTransportContext.create(Map.of("user", currentUser.name.get())));
		}

		/** Turns the identity into an HTTP header, as a token-adding hook would. */
		@Bean
		McpHttpRequestCustomizer userHeaderCustomizer() {
			return (request, headers, context) -> {
				headers.set(USER_HEADER, String.valueOf(context.get("user")));
				return Mono.empty();
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	@ImportAutoConfiguration({ McpServerJsonMapperAutoConfiguration.class, McpServerModernAutoConfiguration.class,
			McpServerModernWebMvcAutoConfiguration.class, McpServerAnnotationScannerAutoConfiguration.class,
			ModernServerSpecificationFactoryAutoConfiguration.class })
	static class ModernServerApplication {

		@Bean
		SeenUsers seenUsers() {
			return new SeenUsers();
		}

		@Bean
		McpTransportContextExtractor<ServerRequest> userExtractor(SeenUsers seenUsers) {
			return request -> {
				String user = request.headers().firstHeader(USER_HEADER);
				seenUsers.names.add(String.valueOf(user));
				return McpTransportContext.create(Map.of("user", String.valueOf(user)));
			};
		}

		@Bean
		GreetingTools greetingTools() {
			return new GreetingTools();
		}

	}

	static class GreetingTools {

		@McpTool(name = "greet", description = "Greets")
		public String greet(@McpToolParam(description = "name") String name) {
			return "Hello " + name;
		}

	}

}
