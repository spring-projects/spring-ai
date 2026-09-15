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
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.method.changed.resource.SyncResourceListChangedSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SyncMcpResourceListChangedProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpResourceListChangedProviderTests {

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
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of(handler));

		List<SyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
		List<Consumer<List<McpSchema.Resource>>> consumers = specifications.stream()
			.map(SyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		// Should find 2 annotated methods
		assertThat(consumers).hasSize(2);
		assertThat(specifications).hasSize(2);

		// Test the first consumer
		consumers.get(0).accept(TEST_RESOURCES);

		// Verify that the method was called
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
		assertThat(handler.lastUpdatedResources).hasSize(2);
		assertThat(handler.lastUpdatedResources.get(0).name()).isEqualTo("test-resource-1");
		assertThat(handler.lastUpdatedResources.get(1).name()).isEqualTo("test-resource-2");

		// Test the second consumer
		consumers.get(1).accept(TEST_RESOURCES);

		// Verify that the method was called
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
	}

	@Test
	void testClientIdSpecifications() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of(handler));

		List<SyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should find 2 specifications
		assertThat(specifications).hasSize(2);

		// Check client IDs
		List<String> clientIds = specifications.stream().map(spec -> spec.clients()).flatMap(Stream::of).toList();

		assertThat(clientIds).containsExactlyInAnyOrder("client1", "test-client");
	}

	@Test
	void testEmptyList() {
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of());

		List<Consumer<List<McpSchema.Resource>>> consumers = provider.getResourceListChangedSpecifications()
			.stream()
			.map(SyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		ResourceListChangedHandler handler1 = new ResourceListChangedHandler();
		ResourceListChangedHandler handler2 = new ResourceListChangedHandler();
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(
				List.of(handler1, handler2));

		List<Consumer<List<McpSchema.Resource>>> consumers = provider.getResourceListChangedSpecifications()
			.stream()
			.map(SyncResourceListChangedSpecification::resourceListChangeHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	@Test
	void testConsumerFunctionality() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of(handler));

		List<SyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
		Consumer<List<McpSchema.Resource>> consumer = specifications.get(0).resourceListChangeHandler();

		// Test with empty list
		List<McpSchema.Resource> emptyList = List.of();
		consumer.accept(emptyList);
		assertThat(handler.lastUpdatedResources).isEqualTo(emptyList);
		assertThat(handler.lastUpdatedResources).isEmpty();

		// Test with test resources
		consumer.accept(TEST_RESOURCES);
		assertThat(handler.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
		assertThat(handler.lastUpdatedResources).hasSize(2);
	}

	@Test
	void testNonAnnotatedMethodsIgnored() {
		ResourceListChangedHandler handler = new ResourceListChangedHandler();
		SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of(handler));

		List<SyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();

		// Should only find annotated methods, not the non-annotated one
		assertThat(specifications).hasSize(2);
	}

	/**
	 * Test class with resource list changed consumer methods.
	 */
	static class ResourceListChangedHandler {

		private List<McpSchema.Resource> lastUpdatedResources;

		@McpResourceListChanged(clients = "client1")
		public void handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
			this.lastUpdatedResources = updatedResources;
		}

		@McpResourceListChanged(clients = "test-client")
		public void handleResourceListChangedWithClientId(List<McpSchema.Resource> updatedResources) {
			this.lastUpdatedResources = updatedResources;
		}

		// This method is not annotated and should be ignored
		public void notAnnotatedMethod(List<McpSchema.Resource> updatedResources) {
			// This method should be ignored
		}

	}

}
