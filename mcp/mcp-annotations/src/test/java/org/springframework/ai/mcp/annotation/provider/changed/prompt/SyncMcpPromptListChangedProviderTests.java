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

package org.springframework.ai.mcp.annotation.provider.changed.prompt;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.method.changed.prompt.SyncPromptListChangedSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SyncMcpPromptListChangedProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpPromptListChangedProviderTests {

	private static final List<McpSchema.Prompt> TEST_PROMPTS = List.of(
			new McpSchema.Prompt("test-prompt-1", "Test Prompt 1", List.of()),
			new McpSchema.Prompt("test-prompt-2", "Test Prompt 2", List.of()));

	@Test
	void testGetPromptListChangedSpecifications() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(handler));

		List<SyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
		List<Consumer<List<McpSchema.Prompt>>> consumers = specifications.stream()
			.map(SyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		// Should find 2 annotated methods
		assertThat(consumers).hasSize(2);
		assertThat(specifications).hasSize(2);

		// Test the first consumer
		consumers.get(0).accept(TEST_PROMPTS);

		// Verify that the method was called
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(handler.lastUpdatedPrompts).hasSize(2);
		assertThat(handler.lastUpdatedPrompts.get(0).name()).isEqualTo("test-prompt-1");
		assertThat(handler.lastUpdatedPrompts.get(1).name()).isEqualTo("test-prompt-2");

		// Test the second consumer
		consumers.get(1).accept(TEST_PROMPTS);

		// Verify that the method was called
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
	}

	@Test
	void testClientIdSpecifications() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(handler));

		List<SyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should find 2 specifications
		assertThat(specifications).hasSize(2);

		// Check client IDs
		List<String> clientIds = specifications.stream().map(spec -> spec.clients()).flatMap(Stream::of).toList();

		assertThat(clientIds).containsExactlyInAnyOrder("test-client", "my-client-id");
	}

	@Test
	void testEmptyList() {
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of());

		List<Consumer<List<McpSchema.Prompt>>> consumers = provider.getPromptListChangedSpecifications()
			.stream()
			.map(SyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		PromptListChangedHandler handler1 = new PromptListChangedHandler();
		PromptListChangedHandler handler2 = new PromptListChangedHandler();
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(handler1, handler2));

		List<Consumer<List<McpSchema.Prompt>>> consumers = provider.getPromptListChangedSpecifications()
			.stream()
			.map(SyncPromptListChangedSpecification::promptListChangeHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	@Test
	void testConsumerFunctionality() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(handler));

		List<SyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
		Consumer<List<McpSchema.Prompt>> consumer = specifications.get(0).promptListChangeHandler();

		// Test with empty list
		List<McpSchema.Prompt> emptyList = List.of();
		consumer.accept(emptyList);
		assertThat(handler.lastUpdatedPrompts).isEqualTo(emptyList);
		assertThat(handler.lastUpdatedPrompts).isEmpty();

		// Test with test prompts
		consumer.accept(TEST_PROMPTS);
		assertThat(handler.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(handler.lastUpdatedPrompts).hasSize(2);
	}

	@Test
	void testNonAnnotatedMethodsIgnored() {
		PromptListChangedHandler handler = new PromptListChangedHandler();
		SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(handler));

		List<SyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();

		// Should only find annotated methods, not the non-annotated one
		assertThat(specifications).hasSize(2);
	}

	/**
	 * Test class with prompt list changed consumer methods.
	 */
	static class PromptListChangedHandler {

		private List<McpSchema.Prompt> lastUpdatedPrompts;

		@McpPromptListChanged(clients = "my-client-id")
		public void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			this.lastUpdatedPrompts = updatedPrompts;
		}

		@McpPromptListChanged(clients = "test-client")
		public void handlePromptListChangedWithClientId(List<McpSchema.Prompt> updatedPrompts) {
			this.lastUpdatedPrompts = updatedPrompts;
		}

		// This method is not annotated and should be ignored
		public void notAnnotatedMethod(List<McpSchema.Prompt> updatedPrompts) {
			// This method should be ignored
		}

	}

}
