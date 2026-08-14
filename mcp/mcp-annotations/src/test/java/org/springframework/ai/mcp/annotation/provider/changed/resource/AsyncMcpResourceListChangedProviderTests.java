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

package org.springframework.ai.mcp.annotation.provider.changed.resource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.method.changed.resource.AsyncResourceListChangedSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AsyncMcpResourceListChangedProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpResourceListChangedProviderTests {

	private static final List<McpSchema.Resource> TEST_RESOURCES = List.of(
			McpSchema.Resource.builder("file:///test1.txt", "test-resource-1")
				.description("Test Resource 1")
				.mimeType("text/plain")
				.build(),
			McpSchema.Resource.builder("file:///test2.txt", "test-resource-2")
				.description("Test Resource 2")
				.mimeType("text/plain")
				.build());

	@Test
	void testGetResourceListChangedSpecifications() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
		List<Function<List<McpSchema.Resource>, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		// Should find 2 annotated methods (2 Mono<Void>. Ignores the void method)
		assertThat(consumers).hasSize(2);
		assertThat(specifications).hasSize(2);

		// Test the first consumer
		StepVerifier.create(consumers.get(0).apply(TEST_RESOURCES)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
		assertThat(handler.lastUpdatedResources).hasSize(2);
		assertThat(handler.lastUpdatedResources.get(0).name()).isEqualTo("test-resource-1");
		assertThat(handler.lastUpdatedResources.get(1).name()).isEqualTo("test-resource-2");

		// Test the second consumer
		StepVerifier.create(consumers.get(0).apply(TEST_RESOURCES)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);

		// Test the third consumer (void method)
		StepVerifier.create(consumers.get(1).apply(TEST_RESOURCES)).verifyComplete();

		// Verify that the method was called
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
	}

	@Test
	void testClientIdSpecifications() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should find 3 specifications
		assertThat(specifications).hasSize(2);

		// Check client IDs
		List<String> clientIds = specifications.stream().map(spec -> spec.clients()).flatMap(Stream::of).toList();

		assertThat(clientIds).containsExactlyInAnyOrder("client1", "test-client");
	}

	@Test
	void testEmptyList() {
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of());

		List<Function<List<McpSchema.Resource>, Mono<Void>>> consumers = provider.getResourceListChangedSpecifications()
			.stream()
			.map(AsyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		ResourceListChangedHandler handler1 = new ResourceListChangedHandler();
		ResourceListChangedHandler handler2 = new ResourceListChangedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(
				List.of(handler1, handler2));

		List<Function<List<McpSchema.Resource>, Mono<Void>>> consumers = provider.getResourceListChangedSpecifications()
			.stream()
			.map(AsyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler) drops the non-reactive
		// ones
		assertThat(consumers).hasSize(4);
	}

	@Test
	void testConsumerFunctionality() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
		Function<List<McpSchema.Resource>, Mono<Void>> consumer = specifications.get(0).resourceListChangeHandler();

		// Test with empty list
		List<McpSchema.Resource> emptyList = List.of();
		StepVerifier.create(consumer.apply(emptyList)).verifyComplete();
		assertThat(handler.lastUpdatedResources).isEqualTo(emptyList);
		assertThat(handler.lastUpdatedResources).isEmpty();

		// Test with test resources
		StepVerifier.create(consumer.apply(TEST_RESOURCES)).verifyComplete();
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
		assertThat(handler.lastUpdatedResources).hasSize(2);
	}

	@Test
	void testNonAnnotatedMethodsIgnored() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should only find annotated methods, not the non-annotated one and drops the
		// non-reactive ones
		assertThat(specifications).hasSize(2);
	}

	@Test
	void testInvalidReturnTypesFiltered() {
		InvalidReturnTypeHandler handler = new InvalidReturnTypeHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should find no methods since they have invalid return types
		assertThat(specifications).isEmpty();
	}

	@Test
	void testMixedValidAndInvalidMethods() {
		MixedHandler handler = new MixedHandler();
		AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(handler));

		List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should find only 1 valid method (Mono<Void> and drop the non-reactive void)
		assertThat(specifications).hasSize(1);

		// Test that the valid methods work
		Function<List<McpSchema.Resource>, Mono<Void>> consumer = specifications.get(0).resourceListChangeHandler();
		StepVerifier.create(consumer.apply(TEST_RESOURCES)).verifyComplete();
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
	}

	/**
	 * Test class with methods that should be filtered out (non-reactive return types).
	 */
	static class InvalidReturnTypeHandler {

		@McpResourceListChanged(clients = "client1")
		public String invalidReturnType(List<McpSchema.Resource> updatedResources) {
			return "Invalid";
		}

		@McpResourceListChanged(clients = "client1")
		public int anotherInvalidReturnType(List<McpSchema.Resource> updatedResources) {
			return 42;
		}

	}

	/**
	 * Test class with mixed valid and invalid methods.
	 */
	static class MixedHandler {

		private List<McpSchema.Resource> lastUpdatedResources;

		@McpResourceListChanged(clients = "client1")
		public Mono<Void> validMethod(List<McpSchema.Resource> updatedResources) {
			return Mono.fromRunnable(() -> this.lastUpdatedResources = updatedResources);
		}

		@McpResourceListChanged(clients = "client1")
		public void validVoidMethod(List<McpSchema.Resource> updatedResources) {
			this.lastUpdatedResources = updatedResources;
		}

		@McpResourceListChanged(clients = "client1")
		public String invalidMethod(List<McpSchema.Resource> updatedResources) {
			return "Invalid";
		}

	}

	/**
	 * Test class with resource list changed consumer methods.
	 */
	static class ResourceListChangedHandler {

		private List<McpSchema.Resource> lastUpdatedResources;

		@McpResourceListChanged(clients = "client1")
		public Mono<Void> handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
			return Mono.fromRunnable(() -> this.lastUpdatedResources = updatedResources);
		}

		@McpResourceListChanged(clients = "test-client")
		public Mono<Void> handleResourceListChangedWithClientId(List<McpSchema.Resource> updatedResources) {
			return Mono.fromRunnable(() -> this.lastUpdatedResources = updatedResources);
		}

		@McpResourceListChanged(clients = "client1")
		public void handleResourceListChangedVoid(List<McpSchema.Resource> updatedResources) {
			this.lastUpdatedResources = updatedResources;
		}

		// This method is not annotated and should be ignored
		public Mono<Void> notAnnotatedMethod(List<McpSchema.Resource> updatedResources) {
			return Mono.empty();
		}

	}

}
