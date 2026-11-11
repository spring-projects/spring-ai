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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.modern.InMemoryAsyncToolRepository;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcModernServerTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpServerModernWebMvcAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerModernAutoConfiguration.class, McpServerModernWebMvcAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcModernServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasBean("webMvcModernServerRouterFunction");
			assertThat(context).hasSingleBean(SmartLifecycle.class);
			assertThat(context.getBean(WebMvcModernServerTransport.class)).extracting("mcpEndpoint").isEqualTo("/mcp");
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.mcp-endpoint=/api/mcp")
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class)).extracting("mcpEndpoint")
				.isEqualTo("/api/mcp"));
	}

	@Test
	void zeroKeepAliveIntervalDisablesKeepAlives() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.keep-alive-interval=0s")
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class))
				.extracting("keepAliveInterval")
				.isNull());
	}

	@Test
	@SuppressWarnings("unchecked")
	void transportContextIsEmptyByDefault() {
		this.contextRunner.run(context -> {
			McpTransportContextExtractor<ServerRequest> extractor = (McpTransportContextExtractor<ServerRequest>) ReflectionTestUtils
				.getField(context.getBean(WebMvcModernServerTransport.class), "contextExtractor");

			MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp");
			servletRequest.addHeader("X-Role", "admin");
			servletRequest.addHeader("Authorization", "Bearer t");
			ServerRequest request = ServerRequest.create(servletRequest, List.of());
			assertThat(extractor.extract(request)).isSameAs(McpTransportContext.EMPTY);
		});
	}

	@Test
	void userExtractorFillsTheTransportContext() {
		this.contextRunner.withUserConfiguration(ExtractorConfiguration.class)
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class))
				.extracting("contextExtractor")
				.isSameAs(context.getBean(McpTransportContextExtractor.class)));
	}

	@Test
	void inactiveWhenDisabledOrOtherProtocolOrStdio() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void asyncTypeServesReactiveToolsOverHttp() throws Exception {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withConfiguration(AutoConfigurations.of(McpServerAnnotationScannerAutoConfiguration.class,
					ModernServerSpecificationFactoryAutoConfiguration.class))
			.withUserConfiguration(AsyncToolsConfiguration.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(InMemoryAsyncToolRepository.class);
				RouterFunction<ServerResponse> router = context.getBean(RouterFunction.class);

				MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp");
				servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
				servletRequest.addHeader("Mcp-Method", McpSchema.METHOD_TOOLS_CALL);
				servletRequest.addHeader("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION);
				servletRequest.addHeader("Mcp-Name", "greet");
				servletRequest.setContent(toolsCall("greet", Map.of("name", "Ann")).getBytes());
				List<HttpMessageConverter<?>> converters = List.of(new StringHttpMessageConverter());
				ServerRequest request = ServerRequest.create(servletRequest, converters);

				// The reactive handler is subscribed on the container thread, as the
				// transport does for every request but subscriptions/listen.
				ServerResponse response = router.route(request).orElseThrow().handle(request);
				MockHttpServletResponse servletResponse = new MockHttpServletResponse();
				response.writeTo(servletRequest, servletResponse, () -> converters);
				assertThat(servletResponse.getStatus()).isEqualTo(200);
				assertThat(servletResponse.getContentAsString()).contains("Hello Ann");
			});
	}

	private static String toolsCall(String tool, Map<String, Object> arguments) {
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		Map<String, Object> params = new HashMap<>();
		params.put("_meta", meta);
		params.put("name", tool);
		params.put("arguments", arguments);
		try {
			return McpJsonDefaults.getMapper()
				.writeValueAsString(new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1, params));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Configuration
	static class AsyncToolsConfiguration {

		@Bean
		AsyncTools asyncTools() {
			return new AsyncTools();
		}

	}

	static class AsyncTools {

		@McpTool(name = "greet", description = "Greets")
		public Mono<String> greet(@McpToolParam(description = "name") String name) {
			return Mono.just("Hello " + name);
		}

	}

	@Configuration
	static class ExtractorConfiguration {

		@Bean
		McpTransportContextExtractor<ServerRequest> tenantExtractor() {
			return request -> McpTransportContext.create(Map.of("tenant", "acme"));
		}

	}

}
