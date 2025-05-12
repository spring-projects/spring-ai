/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 */
class ToolCallingChatOptionsTests {

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledTrue() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setInternalToolExecutionEnabled(true);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledFalse() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setInternalToolExecutionEnabled(false);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isFalse();
	}

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledDefault() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenMergeRuntimeAndDefaultToolNames() {
		Set<String> runtimeToolNames = Set.of("toolA");
		Set<String> defaultToolNames = Set.of("toolB");
		Set<String> mergedToolNames = ToolCallingChatOptions.mergeToolNames(runtimeToolNames, defaultToolNames);
		assertThat(mergedToolNames).containsExactlyInAnyOrder("toolA");
	}

	@Test
	void whenMergeRuntimeAndEmptyDefaultToolNames() {
		Set<String> runtimeToolNames = Set.of("toolA");
		Set<String> defaultToolNames = Set.of();
		Set<String> mergedToolNames = ToolCallingChatOptions.mergeToolNames(runtimeToolNames, defaultToolNames);
		assertThat(mergedToolNames).containsExactlyInAnyOrder("toolA");
	}

	@Test
	void whenMergeEmptyRuntimeAndDefaultToolNames() {
		Set<String> runtimeToolNames = Set.of();
		Set<String> defaultToolNames = Set.of("toolB");
		Set<String> mergedToolNames = ToolCallingChatOptions.mergeToolNames(runtimeToolNames, defaultToolNames);
		assertThat(mergedToolNames).containsExactlyInAnyOrder("toolB");
	}

	@Test
	void whenMergeEmptyRuntimeAndEmptyDefaultToolNames() {
		Set<String> runtimeToolNames = Set.of();
		Set<String> defaultToolNames = Set.of();
		Set<String> mergedToolNames = ToolCallingChatOptions.mergeToolNames(runtimeToolNames, defaultToolNames);
		assertThat(mergedToolNames).containsExactlyInAnyOrder();
	}

	@Test
	void whenMergeRuntimeAndDefaultToolCallbacks() {
		List<ToolCallback> runtimeToolCallbacks = List.of(new TestToolCallback("toolA"));
		List<ToolCallback> defaultToolCallbacks = List.of(new TestToolCallback("toolB"));
		List<ToolCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(1);
		assertThat(mergedToolCallbacks.get(0).getToolDefinition().name()).isEqualTo("toolA");
	}

	@Test
	void whenMergeRuntimeAndEmptyDefaultToolCallbacks() {
		List<ToolCallback> runtimeToolCallbacks = List.of(new TestToolCallback("toolA"));
		List<ToolCallback> defaultToolCallbacks = List.of();
		List<ToolCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(1);
		assertThat(mergedToolCallbacks.get(0).getToolDefinition().name()).isEqualTo("toolA");
	}

	@Test
	void whenMergeEmptyRuntimeAndDefaultToolCallbacks() {
		List<ToolCallback> runtimeToolCallbacks = List.of();
		List<ToolCallback> defaultToolCallbacks = List.of(new TestToolCallback("toolB"));
		List<ToolCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(1);
		assertThat(mergedToolCallbacks.get(0).getToolDefinition().name()).isEqualTo("toolB");
	}

	@Test
	void whenMergeEmptyRuntimeAndEmptyDefaultToolCallbacks() {
		List<ToolCallback> runtimeToolCallbacks = List.of();
		List<ToolCallback> defaultToolCallbacks = List.of();
		List<ToolCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(0);
	}

	@Test
	void whenMergeRuntimeAndDefaultToolContext() {
		Map<String, Object> runtimeToolContext = Map.of("key1", "value1", "key2", "value2");
		Map<String, Object> defaultToolContext = Map.of("key1", "valueA", "key3", "value3");
		Map<String, Object> mergedToolContext = ToolCallingChatOptions.mergeToolContext(runtimeToolContext,
				defaultToolContext);
		assertThat(mergedToolContext).hasSize(3);
		assertThat(mergedToolContext).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("key3", "value3");
	}

	@Test
	void whenMergeRuntimeAndEmptyDefaultToolContext() {
		Map<String, Object> runtimeToolContext = Map.of("key1", "value1", "key2", "value2");
		Map<String, Object> defaultToolContext = Map.of();
		Map<String, Object> mergedToolContext = ToolCallingChatOptions.mergeToolContext(runtimeToolContext,
				defaultToolContext);
		assertThat(mergedToolContext).hasSize(2);
		assertThat(mergedToolContext).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	void whenMergeEmptyRuntimeAndDefaultToolContext() {
		Map<String, Object> runtimeToolContext = Map.of();
		Map<String, Object> defaultToolContext = Map.of("key1", "value1", "key2", "value2");
		Map<String, Object> mergedToolContext = ToolCallingChatOptions.mergeToolContext(runtimeToolContext,
				defaultToolContext);
		assertThat(mergedToolContext).hasSize(2);
		assertThat(mergedToolContext).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	void whenMergeEmptyRuntimeAndEmptyDefaultToolContext() {
		Map<String, Object> runtimeToolContext = Map.of();
		Map<String, Object> defaultToolContext = Map.of();
		Map<String, Object> mergedToolContext = ToolCallingChatOptions.mergeToolContext(runtimeToolContext,
				defaultToolContext);
		assertThat(mergedToolContext).hasSize(0);
	}

	@Test
	void shouldEnsureUniqueToolNames() {
		List<ToolCallback> toolCallbacks = List.of(new TestToolCallback("toolA"), new TestToolCallback("toolA"));
		assertThatThrownBy(() -> ToolCallingChatOptions.validateToolCallbacks(toolCallbacks))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name (toolA)");
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
