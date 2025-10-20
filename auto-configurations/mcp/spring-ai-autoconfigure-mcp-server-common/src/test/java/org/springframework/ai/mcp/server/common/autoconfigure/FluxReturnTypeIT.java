/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify the fix for Issue #4542: Stateless Async MCP Server with
 * streamable-http returns only the first element from tools with a Flux return type.
 *
 * @author liugddx
 */
public class FluxReturnTypeIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerStatelessAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				StatelessToolCallbackConverterAutoConfiguration.class));

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * This test verifies that @McpTool methods returning Flux<T> now properly return all
	 * elements after the fix.
	 */
	@Test
	void testFluxReturnTypeReturnsAllElements() {
		this.contextRunner.withUserConfiguration(FluxToolConfiguration.class)
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.protocol=STATELESS",
					"spring.ai.mcp.server.annotation.enabled=true")
			.run(context -> {
				assertThat(context).hasBean("fluxTestTools");

				// Get the tool specifications
				List<McpStatelessServerFeatures.AsyncToolSpecification> toolSpecs = context.getBean("toolSpecs",
						List.class);
				assertThat(toolSpecs).isNotEmpty();

				// Find the flux-test tool
				McpStatelessServerFeatures.AsyncToolSpecification fluxTestTool = toolSpecs.stream()
					.filter(spec -> spec.tool().name().equals("flux-test"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("flux-test tool not found"));

				// Call the tool with count=3
				McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
					.name("flux-test")
					.arguments(Map.of("count", 3))
					.build();

				McpSchema.CallToolResult result = fluxTestTool.callHandler()
					.apply(new McpTransportContext(Map.of()), request)
					.block();

				assertThat(result).isNotNull();
				assertThat(result.isError()).isFalse();
				assertThat(result.content()).hasSize(1);

				// Verify all three elements are present in the result
				String content = ((McpSchema.TextContent) result.content().get(0)).text();
				List<String> items = this.objectMapper.readValue(content, List.class);

				assertThat(items).containsExactly("item-1", "item-2", "item-3");
			});
	}

	/**
	 * This test verifies that @McpTool methods returning Flux<Object> with complex
	 * objects properly return all elements.
	 */
	@Test
	void testFluxReturnTypeWithComplexObjects() {
		this.contextRunner.withUserConfiguration(FluxToolConfiguration.class)
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.protocol=STATELESS",
					"spring.ai.mcp.server.annotation.enabled=true")
			.run(context -> {
				assertThat(context).hasBean("fluxTestTools");

				List<McpStatelessServerFeatures.AsyncToolSpecification> toolSpecs = context.getBean("toolSpecs",
						List.class);

				McpStatelessServerFeatures.AsyncToolSpecification fluxDataTool = toolSpecs.stream()
					.filter(spec -> spec.tool().name().equals("flux-data-stream"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("flux-data-stream tool not found"));

				McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
					.name("flux-data-stream")
					.arguments(Map.of("category", "test"))
					.build();

				McpSchema.CallToolResult result = fluxDataTool.callHandler()
					.apply(new McpTransportContext(Map.of()), request)
					.block();

				assertThat(result).isNotNull();
				assertThat(result.isError()).isFalse();

				String content = ((McpSchema.TextContent) result.content().get(0)).text();
				List<Map<String, String>> items = this.objectMapper.readValue(content, List.class);

				assertThat(items).hasSize(3);
				assertThat(items.get(0)).containsEntry("id", "id1");
				assertThat(items.get(1)).containsEntry("id", "id2");
				assertThat(items.get(2)).containsEntry("id", "id3");
			});
	}

	/**
	 * This test demonstrates that the workaround using Mono<List<T>> continues to work
	 * properly.
	 */
	@Test
	void testMonoListWorkaround() {
		this.contextRunner.withUserConfiguration(MonoListToolConfiguration.class)
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.protocol=STATELESS",
					"spring.ai.mcp.server.annotation.enabled=true")
			.run(context -> {
				assertThat(context).hasBean("monoListTestTools");

				List<McpStatelessServerFeatures.AsyncToolSpecification> toolSpecs = context.getBean("toolSpecs",
						List.class);

				McpStatelessServerFeatures.AsyncToolSpecification monoListTool = toolSpecs.stream()
					.filter(spec -> spec.tool().name().equals("mono-list-test"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("mono-list-test tool not found"));

				McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
					.name("mono-list-test")
					.arguments(Map.of("count", 3))
					.build();

				McpSchema.CallToolResult result = monoListTool.callHandler()
					.apply(new McpTransportContext(Map.of()), request)
					.block();

				assertThat(result).isNotNull();
				assertThat(result.isError()).isFalse();

				// The workaround should also return all three elements
				String content = ((McpSchema.TextContent) result.content().get(0)).text();
				List<String> items = this.objectMapper.readValue(content, List.class);

				assertThat(items).containsExactly("item-1", "item-2", "item-3");
			});
	}

	@Configuration
	static class FluxToolConfiguration {

		@Bean
		FluxTestTools fluxTestTools() {
			return new FluxTestTools();
		}

	}

	@Component
	static class FluxTestTools {

		/**
		 * This method demonstrates the bug: it returns Flux<String> but only the first
		 * element is returned to the client.
		 */
		@McpTool(name = "flux-test", description = "Test Flux return type - BUGGY")
		public Flux<String> getMultipleItems(
				@McpToolParam(description = "Number of items to return", required = true) int count) {
			return Flux.range(1, count).map(i -> "item-" + i);
		}

		/**
		 * This method also demonstrates the bug with a more realistic streaming scenario.
		 */
		@McpTool(name = "flux-data-stream", description = "Stream data items - BUGGY")
		public Flux<DataItem> streamDataItems(
				@McpToolParam(description = "Category to filter", required = false) String category) {
			return Flux.just(new DataItem("id1", "Item 1", category), new DataItem("id2", "Item 2", category),
					new DataItem("id3", "Item 3", category));
		}

	}

	@Configuration
	static class MonoListToolConfiguration {

		@Bean
		MonoListTestTools monoListTestTools() {
			return new MonoListTestTools();
		}

	}

	@Component
	static class MonoListTestTools {

		/**
		 * WORKAROUND: Use Mono<List<T>> instead of Flux<T> to return all elements.
		 */
		@McpTool(name = "mono-list-test", description = "Test Mono<List> workaround")
		public Mono<List<String>> getMultipleItems(
				@McpToolParam(description = "Number of items to return", required = true) int count) {
			return Flux.range(1, count).map(i -> "item-" + i).collectList();
		}

		/**
		 * WORKAROUND: Collect Flux elements into a list before returning.
		 */
		@McpTool(name = "mono-list-data-stream", description = "Get data items as list")
		public Mono<List<DataItem>> getDataItems(
				@McpToolParam(description = "Category to filter", required = false) String category) {
			return Flux
				.just(new DataItem("id1", "Item 1", category), new DataItem("id2", "Item 2", category),
						new DataItem("id3", "Item 3", category))
				.collectList();
		}

	}

	record DataItem(String id, String name, String category) {
	}

}
