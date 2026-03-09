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

package org.springframework.ai.mcp.annotation.provider.changed.tool;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.method.changed.tool.AsyncToolListChangedSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AsyncMcpToolListChangedProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpToolListChangedProviderTests {

	private static final List<McpSchema.Tool> TEST_TOOLS = List.of(
			McpSchema.Tool.builder()
				.name("test-tool-1")
				.description("Test Tool 1")
				.inputSchema(McpJsonDefaults.getMapper(), "{}")
				.build(),
			McpSchema.Tool.builder()
				.name("test-tool-2")
				.description("Test Tool 2")
				.inputSchema(McpJsonDefaults.getMapper(), "{}")
				.build());

	@Test
	void testGetToolListChangedSpecifications() {
		ToolListChangedHandler handler = new ToolListChangedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();
		List<Function<List<McpSchema.Tool>, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncToolListChangedSpecification::toolListChangeHandler)
			.toList();

		// Should find 2 annotated methods (2 Mono<Void>. Ignores the void method)
		assertThat(consumers).hasSize(2);
		assertThat(specifications).hasSize(2);

		// Test the first consumer
		StepVerifier.create(consumers.get(0).apply(TEST_TOOLS)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedTools).isEqualTo(TEST_TOOLS);
		assertThat(handler.lastUpdatedTools).hasSize(2);
		assertThat(handler.lastUpdatedTools.get(0).name()).isEqualTo("test-tool-1");
		assertThat(handler.lastUpdatedTools.get(1).name()).isEqualTo("test-tool-2");

		// Test the second consumer
		StepVerifier.create(consumers.get(1).apply(TEST_TOOLS)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedTools).isEqualTo(TEST_TOOLS);

		// Verify that the method was called
		assertThat(handler.lastUpdatedTools).isEqualTo(TEST_TOOLS);
	}

	@Test
	void testClientIdSpecifications() {
		ToolListChangedHandler handler = new ToolListChangedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();

		// Should find 2 specifications. Ignore the non-reactive method
		assertThat(specifications).hasSize(2);

		// Check client IDs
		List<String> clientIds = specifications.stream().map(spec -> spec.clients()).flatMap(Stream::of).toList();

		assertThat(clientIds).containsExactlyInAnyOrder("client1", "test-client");
	}

	@Test
	void testEmptyList() {
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of());

		List<Function<List<McpSchema.Tool>, Mono<Void>>> consumers = provider.getToolListChangedSpecifications()
			.stream()
			.map(AsyncToolListChangedSpecification::toolListChangeHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		ToolListChangedHandler handler1 = new ToolListChangedHandler();
		ToolListChangedHandler handler2 = new ToolListChangedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler1, handler2));

		List<Function<List<McpSchema.Tool>, Mono<Void>>> consumers = provider.getToolListChangedSpecifications()
			.stream()
			.map(AsyncToolListChangedSpecification::toolListChangeHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	@Test
	void testConsumerFunctionality() {
		ToolListChangedHandler handler = new ToolListChangedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();
		Function<List<McpSchema.Tool>, Mono<Void>> consumer = specifications.get(0).toolListChangeHandler();

		// Test with empty list
		List<McpSchema.Tool> emptyList = List.of();
		StepVerifier.create(consumer.apply(emptyList)).verifyComplete();
		assertThat(handler.lastUpdatedTools).isEqualTo(emptyList);
		assertThat(handler.lastUpdatedTools).isEmpty();

		// Test with test tools
		StepVerifier.create(consumer.apply(TEST_TOOLS)).verifyComplete();
		assertThat(handler.lastUpdatedTools).isEqualTo(TEST_TOOLS);
		assertThat(handler.lastUpdatedTools).hasSize(2);
	}

	@Test
	void testNonAnnotatedMethodsIgnored() {
		ToolListChangedHandler handler = new ToolListChangedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();

		// Should only find annotated methods, not the non-annotated one and ignore the
		// non-reactive one
		assertThat(specifications).hasSize(2);
	}

	@Test
	void testInvalidReturnTypesFiltered() {
		InvalidReturnTypeHandler handler = new InvalidReturnTypeHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();

		// Should find no methods since they have invalid return types
		assertThat(specifications).isEmpty();
	}

	@Test
	void testMixedValidAndInvalidMethods() {
		MixedHandler handler = new MixedHandler();
		AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(handler));

		List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();

		// Should find only the 1 valid methods (one Mono<Void>)
		assertThat(specifications).hasSize(1);

		// Test that the valid methods work
		Function<List<McpSchema.Tool>, Mono<Void>> consumer = specifications.get(0).toolListChangeHandler();
		StepVerifier.create(consumer.apply(TEST_TOOLS)).verifyComplete();
		assertThat(handler.lastUpdatedTools).isEqualTo(TEST_TOOLS);
	}

	/**
	 * Test class with mixed valid and invalid methods.
	 */
	static class MixedHandler {

		private List<McpSchema.Tool> lastUpdatedTools;

		@McpToolListChanged(clients = "client1")
		public Mono<Void> validMethod(List<McpSchema.Tool> updatedTools) {
			return Mono.fromRunnable(() -> this.lastUpdatedTools = updatedTools);
		}

		// ignored since it does not return Mono<Void>
		@McpToolListChanged(clients = "client1")
		public void validVoidMethod(List<McpSchema.Tool> updatedTools) {
			this.lastUpdatedTools = updatedTools;
		}

		@McpToolListChanged(clients = "client1")
		public String invalidMethod(List<McpSchema.Tool> updatedTools) {
			return "Invalid";
		}

	}

	/**
	 * Test class with methods that should be filtered out (non-reactive return types).
	 */
	static class InvalidReturnTypeHandler {

		@McpToolListChanged(clients = "client1")
		public String invalidReturnType(List<McpSchema.Tool> updatedTools) {
			return "Invalid";
		}

		@McpToolListChanged(clients = "client1")
		public int anotherInvalidReturnType(List<McpSchema.Tool> updatedTools) {
			return 42;
		}

	}

	/**
	 * Test class with tool list changed consumer methods.
	 */
	static class ToolListChangedHandler {

		private List<McpSchema.Tool> lastUpdatedTools;

		@McpToolListChanged(clients = "client1")
		public Mono<Void> handleToolListChanged(List<McpSchema.Tool> updatedTools) {
			return Mono.fromRunnable(() -> this.lastUpdatedTools = updatedTools);
		}

		@McpToolListChanged(clients = "test-client")
		public Mono<Void> handleToolListChangedWithClientId(List<McpSchema.Tool> updatedTools) {
			return Mono.fromRunnable(() -> this.lastUpdatedTools = updatedTools);
		}

		@McpToolListChanged(clients = "client1")
		public void handleToolListChangedVoid(List<McpSchema.Tool> updatedTools) {
			this.lastUpdatedTools = updatedTools;
		}

		// This method is not annotated and should be ignored
		public Mono<Void> notAnnotatedMethod(List<McpSchema.Tool> updatedTools) {
			return Mono.empty();
		}

	}

}
