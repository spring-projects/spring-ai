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
import org.springframework.ai.tool.ToolCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallingChatOptionsTests {

	@Test
	void setToolCallbacksShouldStoreToolCallbacks() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		ToolCallback callback1 = mock(ToolCallback.class);
		ToolCallback callback2 = mock(ToolCallback.class);
		List<FunctionCallback> callbacks = List.of(callback1, callback2);

		options.setToolCallbacks(callbacks);

		assertThat(options.getToolCallbacks()).hasSize(2).containsExactlyElementsOf(callbacks);
	}

	@Test
	void setToolCallbacksWithVarargsShouldStoreToolCallbacks() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		ToolCallback callback1 = mock(ToolCallback.class);
		ToolCallback callback2 = mock(ToolCallback.class);

		options.setToolCallbacks(List.of(callback1, callback2));

		assertThat(options.getToolCallbacks()).hasSize(2).containsExactly(callback1, callback2);
	}

	@Test
	void setToolCallbacksShouldRejectNullList() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		assertThatThrownBy(() -> options.setToolCallbacks(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallbacks cannot be null");
	}

	@Test
	void setToolsShouldStoreTools() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		Set<String> tools = Set.of("tool1", "tool2");

		options.setTools(tools);

		assertThat(options.getTools()).hasSize(2).containsExactlyInAnyOrderElementsOf(tools);
	}

	@Test
	void setToolsWithVarargsShouldStoreTools() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		options.setTools(Set.of("tool1", "tool2"));

		assertThat(options.getTools()).hasSize(2).containsExactlyInAnyOrder("tool1", "tool2");
	}

	@Test
	void setToolsShouldRejectNullSet() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		assertThatThrownBy(() -> options.setTools((Set<String>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tools cannot be null");
	}

	@Test
	void setToolsShouldRejectNullElements() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		Set<String> tools = new HashSet<>();
		tools.add(null);

		assertThatThrownBy(() -> options.setTools(tools)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tools cannot contain null elements");
	}

	@Test
	void setToolsShouldRejectEmptyElements() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		Set<String> tools = new HashSet<>();
		tools.add("");

		assertThatThrownBy(() -> options.setTools(tools)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tools cannot contain empty elements");
	}

	@Test
	void setToolContextShouldStoreContext() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42);

		options.setToolContext(context);

		assertThat(options.getToolContext()).hasSize(2).containsAllEntriesOf(context);
	}

	@Test
	void setToolContextShouldRejectNullMap() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		assertThatThrownBy(() -> options.setToolContext(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolContext cannot be null");
	}

	@Test
	void copyShouldCreateNewInstanceWithSameValues() {
		DefaultToolCallingChatOptions original = new DefaultToolCallingChatOptions();
		ToolCallback callback = mock(ToolCallback.class);
		original.setToolCallbacks(List.of(callback));
		original.setTools(Set.of("tool1"));
		original.setToolContext(Map.of("key", "value"));
		original.setInternalToolExecutionEnabled(true);
		original.setModel("gpt-4");
		original.setTemperature(0.7);

		DefaultToolCallingChatOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original).satisfies(c -> {
			assertThat(c.getToolCallbacks()).isEqualTo(original.getToolCallbacks());
			assertThat(c.getTools()).isEqualTo(original.getTools());
			assertThat(c.getToolContext()).isEqualTo(original.getToolContext());
			assertThat(c.isInternalToolExecutionEnabled()).isEqualTo(original.isInternalToolExecutionEnabled());
			assertThat(c.getModel()).isEqualTo(original.getModel());
			assertThat(c.getTemperature()).isEqualTo(original.getTemperature());
		});
	}

	@Test
	void gettersShouldReturnImmutableCollections() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		ToolCallback callback = mock(ToolCallback.class);
		options.setToolCallbacks(List.of(callback));
		options.setTools(Set.of("tool1"));
		options.setToolContext(Map.of("key", "value"));

		assertThatThrownBy(() -> options.getToolCallbacks().add(mock(ToolCallback.class)))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getTools().add("tool2")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolContext().put("key2", "value2"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void builderShouldCreateOptionsWithAllProperties() {
		ToolCallback callback = mock(ToolCallback.class);
		Map<String, Object> context = Map.of("key", "value");

		ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
			.toolCallbacks(List.of(callback))
			.tools(Set.of("tool1"))
			.toolContext(context)
			.internalToolExecutionEnabled(true)
			.model("gpt-4")
			.temperature(0.7)
			.maxTokens(100)
			.frequencyPenalty(0.5)
			.presencePenalty(0.3)
			.stopSequences(List.of("stop"))
			.topK(3)
			.topP(0.9)
			.build();

		assertThat(options).satisfies(o -> {
			assertThat(o.getToolCallbacks()).containsExactly(callback);
			assertThat(o.getTools()).containsExactly("tool1");
			assertThat(o.getToolContext()).isEqualTo(context);
			assertThat(o.isInternalToolExecutionEnabled()).isTrue();
			assertThat(o.getModel()).isEqualTo("gpt-4");
			assertThat(o.getTemperature()).isEqualTo(0.7);
			assertThat(o.getMaxTokens()).isEqualTo(100);
			assertThat(o.getFrequencyPenalty()).isEqualTo(0.5);
			assertThat(o.getPresencePenalty()).isEqualTo(0.3);
			assertThat(o.getStopSequences()).containsExactly("stop");
			assertThat(o.getTopK()).isEqualTo(3);
			assertThat(o.getTopP()).isEqualTo(0.9);
		});
	}

	@Test
	void builderShouldSupportToolContextAddition() {
		ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
			.toolContext("key1", "value1")
			.toolContext("key2", "value2")
			.build();

		assertThat(options.getToolContext()).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	void deprecatedMethodsShouldWorkCorrectly() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		FunctionCallback callback1 = mock(FunctionCallback.class);
		ToolCallback callback2 = mock(ToolCallback.class);
		options.setFunctionCallbacks(List.of(callback1, callback2));
		assertThat(options.getFunctionCallbacks()).hasSize(2);

		options.setTools(Set.of("tool1"));
		assertThat(options.getFunctions()).containsExactly("tool1");

		options.setFunctions(Set.of("function1"));
		assertThat(options.getTools()).containsExactly("function1");

		options.setInternalToolExecutionEnabled(true);
		assertThat(options.getProxyToolCalls()).isFalse();

		options.setProxyToolCalls(true);
		assertThat(options.isInternalToolExecutionEnabled()).isFalse();
	}

}
