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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.customizer.McpServerCustomizer;
import org.springframework.ai.mcp.modern.InMemoryAsyncToolRepository;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.modern.WebFluxMcpTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpServerModernWebFluxAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerModernAutoConfiguration.class, McpServerModernWebFluxAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxMcpTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasBean("webFluxModernServerRouterFunction");
			assertThat(context).hasSingleBean(SmartLifecycle.class);
			assertThat(routedStatus(context.getBean(RouterFunction.class), "/mcp")).isNotEqualTo(404);
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.mcp-endpoint=/api/mcp").run(context -> {
			RouterFunction<?> router = context.getBean(RouterFunction.class);
			assertThat(routedStatus(router, "/api/mcp")).isNotEqualTo(404);
			assertThat(routedStatus(router, "/mcp")).isEqualTo(404);
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int routedStatus(RouterFunction<?> router, String path) {
		return WebTestClient.bindToRouterFunction((RouterFunction) router)
			.build()
			.post()
			.uri(path)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{}")
			.exchange()
			.returnResult(String.class)
			.getStatus()
			.value();
	}

	@Test
	@SuppressWarnings("unchecked")
	void transportContextIsEmptyByDefault() {
		this.contextRunner.run(context -> {
			McpTransportContextExtractor<ServerRequest> extractor = (McpTransportContextExtractor<ServerRequest>) ReflectionTestUtils
				.getField(context.getBean(WebFluxMcpTransport.class), "contextExtractor");

			ServerRequest request = MockServerRequest.builder()
				.header("X-Role", "admin")
				.header("Authorization", "Bearer t")
				.build();
			assertThat(extractor.extract(request)).isSameAs(McpTransportContext.EMPTY);
		});
	}

	@Test
	void userExtractorFillsTheTransportContext() {
		this.contextRunner.withUserConfiguration(ExtractorConfiguration.class)
			.run(context -> assertThat(context.getBean(WebFluxMcpTransport.class)).extracting("contextExtractor")
				.isSameAs(context.getBean(McpTransportContextExtractor.class)));
	}

	@Test
	void inactiveWhenDisabledOrOtherProtocolOrStdio() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
	}

	@Test
	void multipleCustomizersAreAppliedInOrder() {
		this.contextRunner.withUserConfiguration(OrderedCustomizersConfiguration.class)
			.run(context -> assertThat(context.getBean("customizerInvocations", List.class)).containsExactly("first",
					"second"));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void asyncTypeServesReactiveToolsOverHttp() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withConfiguration(AutoConfigurations.of(McpServerAnnotationScannerAutoConfiguration.class,
					ModernServerSpecificationFactoryAutoConfiguration.class))
			.withUserConfiguration(AsyncToolsConfiguration.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(InMemoryAsyncToolRepository.class);
				WebTestClient client = WebTestClient
					.bindToRouterFunction((RouterFunction) context.getBean(RouterFunction.class))
					.build();

				// A plain result over the non-blocking path.
				String greet = client.post()
					.uri("/mcp")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Mcp-Method", McpSchema.METHOD_TOOLS_CALL)
					.header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION)
					.header("Mcp-Name", "greet")
					.bodyValue(toolsCall("greet", Map.of("name", "Ann"), null))
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
					.expectBody(String.class)
					.returnResult()
					.getResponseBody();
				assertThat(greet).contains("Hello Ann");

				// A streaming result: the notifier pushes progress before the result.
				String progress = client.post()
					.uri("/mcp")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Mcp-Method", McpSchema.METHOD_TOOLS_CALL)
					.header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION)
					.header("Mcp-Name", "progress")
					.bodyValue(toolsCall("progress", Map.of("steps", 2), "tok"))
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
					.expectBody(String.class)
					.returnResult()
					.getResponseBody();
				assertThat(progress).contains(McpSchema.METHOD_NOTIFICATION_PROGRESS).contains("done:2");
			});
	}

	private static String toolsCall(String tool, Map<String, Object> arguments, String progressToken) {
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		if (progressToken != null) {
			meta.put(MetaKeys.PROGRESS_TOKEN, progressToken);
		}
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

		@McpTool(name = "progress", description = "Reports progress")
		public Mono<String> progress(McpAsyncNotifier notifier, @McpToolParam(description = "steps") int steps) {
			return Flux.range(1, steps)
				.concatMap(i -> notifier.progress(i, (double) steps, null))
				.then(Mono.just("done:" + steps));
		}

	}

	@Configuration
	static class ExtractorConfiguration {

		@Bean
		McpTransportContextExtractor<ServerRequest> tenantExtractor() {
			return request -> McpTransportContext.create(Map.of("tenant", "acme"));
		}

	}

	@Configuration
	static class OrderedCustomizersConfiguration {

		@Bean
		List<String> customizerInvocations() {
			return new CopyOnWriteArrayList<>();
		}

		// Declared before "first" so that only @Order can make it run last.
		@Bean
		@Order(2)
		McpServerCustomizer<WebFluxMcpTransport.Builder> secondCustomizer(List<String> customizerInvocations) {
			return builder -> customizerInvocations.add("second");
		}

		@Bean
		@Order(1)
		McpServerCustomizer<WebFluxMcpTransport.Builder> firstCustomizer(List<String> customizerInvocations) {
			return builder -> customizerInvocations.add("first");
		}

	}

}
