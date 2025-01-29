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

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
	void whenFunctionCallingOptionsAndExecutionEnabledTrue() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		options.setProxyToolCalls(false);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenFunctionCallingOptionsAndExecutionEnabledFalse() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		options.setProxyToolCalls(true);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isFalse();
	}

	@Test
	void whenFunctionCallingOptionsAndExecutionEnabledDefault() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenMergeRuntimeAndDefaultToolNames() {
		Set<String> runtimeToolNames = Set.of("toolA");
		Set<String> defaultToolNames = Set.of("toolB");
		Set<String> mergedToolNames = ToolCallingChatOptions.mergeToolNames(runtimeToolNames, defaultToolNames);
		assertThat(mergedToolNames).containsExactlyInAnyOrder("toolA", "toolB");
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
		List<FunctionCallback> runtimeToolCallbacks = List.of(new TestToolCallback("toolA"));
		List<FunctionCallback> defaultToolCallbacks = List.of(new TestToolCallback("toolB"));
		List<FunctionCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(2);
	}

	@Test
	void whenMergeRuntimeAndEmptyDefaultToolCallbacks() {
		List<FunctionCallback> runtimeToolCallbacks = List.of(new TestToolCallback("toolA"));
		List<FunctionCallback> defaultToolCallbacks = List.of();
		List<FunctionCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(1);
	}

	@Test
	void whenMergeEmptyRuntimeAndDefaultToolCallbacks() {
		List<FunctionCallback> runtimeToolCallbacks = List.of();
		List<FunctionCallback> defaultToolCallbacks = List.of(new TestToolCallback("toolB"));
		List<FunctionCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(1);
	}

	@Test
	void whenMergeEmptyRuntimeAndEmptyDefaultToolCallbacks() {
		List<FunctionCallback> runtimeToolCallbacks = List.of();
		List<FunctionCallback> defaultToolCallbacks = List.of();
		List<FunctionCallback> mergedToolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(runtimeToolCallbacks,
				defaultToolCallbacks);
		assertThat(mergedToolCallbacks).hasSize(0);
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		public TestToolCallback(String name) {
			this.toolDefinition = ToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
