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

package org.springframework.ai.mcp.annotation.provider.changed.prompt;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.method.changed.prompt.AsyncPromptListChangedSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AsyncMcpPromptListChangedProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpPromptListChangedProviderTests {

	private static final List<McpSchema.Prompt> TEST_PROMPTS = List.of(
			new McpSchema.Prompt("test-prompt-1", "Test Prompt 1", List.of()),
			new McpSchema.Prompt("test-prompt-2", "Test Prompt 2", List.of()));

	@Test
	void testGetPromptListChangedSpecifications() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
		List<Function<List<McpSchema.Prompt>, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		// Should find 2 annotated methods (2 Mono<Void>)
		assertThat(consumers).hasSize(2);
		assertThat(specifications).hasSize(2);

		// Test the first consumer
		StepVerifier.create(consumers.get(0).apply(TEST_PROMPTS)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(handler.lastUpdatedPrompts).hasSize(2);
		assertThat(handler.lastUpdatedPrompts.get(0).name()).isEqualTo("test-prompt-1");
		assertThat(handler.lastUpdatedPrompts.get(1).name()).isEqualTo("test-prompt-2");

		// Test the second consumer
		StepVerifier.create(consumers.get(1).apply(TEST_PROMPTS)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);

		// Test the third consumer (void method)
		StepVerifier.create(consumers.get(1).apply(TEST_PROMPTS)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
	}

	@Test
	void testClientIdSpecifications() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should find 3 specifications
		assertThat(specifications).hasSize(2);

		// Check client IDs
		List<String> clientIds = specifications.stream().map(spec -> spec.clients()).flatMap(Stream::of).toList();

		assertThat(clientIds).containsExactlyInAnyOrder("my-client-id", "test-client");
	}

	@Test
	void testEmptyList() {
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of());

		List<Function<List<McpSchema.Prompt>, Mono<Void>>> consumers = provider.getPromptListChangedSpecifications()
			.stream()
			.map(AsyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		PromptListChangedHandler handler1 = new PromptListChangedHandler();
		PromptListChangedHandler handler2 = new PromptListChangedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler1, handler2));

		List<Function<List<McpSchema.Prompt>, Mono<Void>>> consumers = provider.getPromptListChangedSpecifications()
			.stream()
			.map(AsyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	@Test
	void testConsumerFunctionality() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
		Function<List<McpSchema.Prompt>, Mono<Void>> consumer = specifications.get(0).promptListChangeHandler();

		// Test with empty list
		List<McpSchema.Prompt> emptyList = List.of();
		StepVerifier.create(consumer.apply(emptyList)).verifyComplete();
		assertThat(handler.lastUpdatedPrompts).isEqualTo(emptyList);
		assertThat(handler.lastUpdatedPrompts).isEmpty();

		// Test with test prompts
		StepVerifier.create(consumer.apply(TEST_PROMPTS)).verifyComplete();
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(handler.lastUpdatedPrompts).hasSize(2);
	}

	@Test
	void testNonAnnotatedMethodsIgnored() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should only find annotated methods, not the non-annotated one
		assertThat(specifications).hasSize(2);
	}

	@Test
	void testInvalidReturnTypesFiltered() {
		InvalidReturnTypeHandler handler = new InvalidReturnTypeHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should find no methods since they have invalid return types
		assertThat(specifications).isEmpty();
	}

	@Test
	void testMixedValidAndInvalidMethods() {
		MixedHandler handler = new MixedHandler();
		AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(handler));

		List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should find only the 2 valid methods (Mono<Void> and void)
		assertThat(specifications).hasSize(1);

		// Test that the valid methods work
		Function<List<McpSchema.Prompt>, Mono<Void>> consumer = specifications.get(0).promptListChangeHandler();
		StepVerifier.create(consumer.apply(TEST_PROMPTS)).verifyComplete();
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
	}

	/**
	 * Test class with methods that should be filtered out (non-reactive return types).
	 */
	static class InvalidReturnTypeHandler {

		@McpPromptListChanged(clients = "my-client-id")
		public String invalidReturnType(List<McpSchema.Prompt> updatedPrompts) {
			return "Invalid";
		}

		@McpPromptListChanged(clients = "my-client-id")
		public int anotherInvalidReturnType(List<McpSchema.Prompt> updatedPrompts) {
			return 42;
		}

	}

	/**
	 * Test class with mixed valid and invalid methods.
	 */
	static class MixedHandler {

		private List<McpSchema.Prompt> lastUpdatedPrompts;

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> validMethod(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.fromRunnable(() -> this.lastUpdatedPrompts = updatedPrompts);
		}

		@McpPromptListChanged(clients = "my-client-id")
		public void validVoidMethod(List<McpSchema.Prompt> updatedPrompts) {
			this.lastUpdatedPrompts = updatedPrompts;
		}

		@McpPromptListChanged(clients = "my-client-id")
		public String invalidMethod(List<McpSchema.Prompt> updatedPrompts) {
			return "Invalid";
		}

	}

	/**
	 * Test class with prompt list changed consumer methods.
	 */
	static class PromptListChangedHandler {

		private List<McpSchema.Prompt> lastUpdatedPrompts;

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.fromRunnable(() -> this.lastUpdatedPrompts = updatedPrompts);
		}

		@McpPromptListChanged(clients = "test-client")
		public Mono<Void> handlePromptListChangedWithClientId(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.fromRunnable(() -> this.lastUpdatedPrompts = updatedPrompts);
		}

		@McpPromptListChanged(clients = "my-client-id")
		public void handlePromptListChangedVoid(List<McpSchema.Prompt> updatedPrompts) {
			this.lastUpdatedPrompts = updatedPrompts;
		}

		// This method is not annotated and should be ignored
		public Mono<Void> notAnnotatedMethod(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.empty();
		}

	}

}
