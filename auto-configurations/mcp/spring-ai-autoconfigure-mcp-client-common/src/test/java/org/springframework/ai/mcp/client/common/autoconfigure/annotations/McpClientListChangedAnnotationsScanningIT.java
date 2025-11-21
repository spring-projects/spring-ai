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

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP client list-changed annotations scanning.
 *
 * <p>
 * This test validates that the annotation scanner correctly identifies and processes
 * {@code @McpToolListChanged}, {@code @McpResourceListChanged}, and
 * {@code @McpPromptListChanged} annotations.
 *
 * @author Fu Jian
 */
public class McpClientListChangedAnnotationsScanningIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class));

	@Test
	public void shouldScanAllThreeListChangedAnnotationsSync() {
		this.contextRunner.withUserConfiguration(AllListChangedConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=SYNC")
			.run(context -> {
				// Verify all three annotations were scanned
				var registry = context.getBean(ClientMcpSyncHandlersRegistry.class);
				var handlers = context.getBean(TestListChangedHandlers.class);
				assertThat(registry).isNotNull();

				List<McpSchema.Tool> updatedTools = List.of(McpSchema.Tool.builder().name("tool-1").build(),
						McpSchema.Tool.builder().name("tool-2").build());
				List<McpSchema.Prompt> updatedPrompts = List.of(
						new McpSchema.Prompt("prompt-1", "a test prompt", Collections.emptyList()),
						new McpSchema.Prompt("prompt-2", "another test prompt", Collections.emptyList()));
				List<McpSchema.Resource> updatedResources = List.of(
						McpSchema.Resource.builder().name("resource-1").uri("file:///resource/1").build(),
						McpSchema.Resource.builder().name("resource-2").uri("file:///resource/2").build());

				registry.handleToolListChanged("test-client", updatedTools);
				registry.handleResourceListChanged("test-client", updatedResources);
				registry.handlePromptListChanged("test-client", updatedPrompts);

				assertThat(handlers.getCalls()).hasSize(3)
					.containsExactlyInAnyOrder(
							new TestListChangedHandlers.Call("resource-list-changed", updatedResources),
							new TestListChangedHandlers.Call("prompt-list-changed", updatedPrompts),
							new TestListChangedHandlers.Call("tool-list-changed", updatedTools));
			});
	}

	@Test
	public void shouldScanAllThreeListChangedAnnotationsAsync() {
		this.contextRunner.withUserConfiguration(AllListChangedConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				// Verify all three annotations were scanned
				var registry = context.getBean(ClientMcpAsyncHandlersRegistry.class);
				var handlers = context.getBean(TestListChangedHandlers.class);
				assertThat(registry).isNotNull();

				List<McpSchema.Tool> updatedTools = List.of(McpSchema.Tool.builder().name("tool-1").build(),
						McpSchema.Tool.builder().name("tool-2").build());
				List<McpSchema.Prompt> updatedPrompts = List.of(
						new McpSchema.Prompt("prompt-1", "a test prompt", Collections.emptyList()),
						new McpSchema.Prompt("prompt-2", "another test prompt", Collections.emptyList()));
				List<McpSchema.Resource> updatedResources = List.of(
						McpSchema.Resource.builder().name("resource-1").uri("file:///resource/1").build(),
						McpSchema.Resource.builder().name("resource-2").uri("file:///resource/2").build());

				registry.handleToolListChanged("test-client", updatedTools).block();
				registry.handleResourceListChanged("test-client", updatedResources).block();
				registry.handlePromptListChanged("test-client", updatedPrompts).block();

				assertThat(handlers.getCalls()).hasSize(3)
					.containsExactlyInAnyOrder(
							new TestListChangedHandlers.Call("resource-list-changed", updatedResources),
							new TestListChangedHandlers.Call("prompt-list-changed", updatedPrompts),
							new TestListChangedHandlers.Call("tool-list-changed", updatedTools));
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { "SYNC", "ASYNC" })
	void shouldNotScanAnnotationsWhenScannerDisabled(String clientType) {
		String prefix = clientType.toLowerCase();

		this.contextRunner.withUserConfiguration(AllListChangedConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=" + clientType,
					"spring.ai.mcp.client.annotation-scanner.enabled=false")
			.run(context -> {
				// Verify scanner beans were not created
				assertThat(context).doesNotHaveBean(ClientMcpSyncHandlersRegistry.class);
				assertThat(context).doesNotHaveBean(ClientMcpAsyncHandlersRegistry.class);
			});
	}

	@Configuration
	static class AllListChangedConfiguration {

		@Bean
		TestListChangedHandlers testHandlers() {
			return new TestListChangedHandlers();
		}

	}

	static class TestListChangedHandlers {

		private final List<Call> calls = new ArrayList<>();

		public List<Call> getCalls() {
			return this.calls;
		}

		@McpToolListChanged(clients = "test-client")
		public void onToolListChanged(List<McpSchema.Tool> updatedTools) {
			this.calls.add(new Call("tool-list-changed", updatedTools));
		}

		@McpResourceListChanged(clients = "test-client")
		public void onResourceListChanged(List<McpSchema.Resource> updatedResources) {
			this.calls.add(new Call("resource-list-changed", updatedResources));
		}

		@McpPromptListChanged(clients = "test-client")
		public void onPromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			this.calls.add(new Call("prompt-list-changed", updatedPrompts));
		}

		@McpToolListChanged(clients = "test-client")
		public Mono<Void> onToolListChangedReactive(List<McpSchema.Tool> updatedTools) {
			this.calls.add(new Call("tool-list-changed", updatedTools));
			return Mono.empty();
		}

		@McpResourceListChanged(clients = "test-client")
		public Mono<Void> onResourceListChangedReactive(List<McpSchema.Resource> updatedResources) {
			this.calls.add(new Call("resource-list-changed", updatedResources));
			return Mono.empty();
		}

		@McpPromptListChanged(clients = "test-client")
		public Mono<Void> onPromptListChangedReactive(List<McpSchema.Prompt> updatedPrompts) {
			this.calls.add(new Call("prompt-list-changed", updatedPrompts));
			return Mono.empty();
		}

		// Record calls made to this object
		record Call(String name, Object callRequest) {
		}

	}

}
