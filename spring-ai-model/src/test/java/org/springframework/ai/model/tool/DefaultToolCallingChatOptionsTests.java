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

package org.springframework.ai.model.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;

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
	void builderShouldStoreToolCallbacks() {
		ToolCallback callback1 = mock(ToolCallback.class);
		ToolCallback callback2 = mock(ToolCallback.class);
		List<ToolCallback> callbacks = List.of(callback1, callback2);

		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();

		assertThat(options.getToolCallbacks()).hasSize(2).containsExactlyElementsOf(callbacks);
	}

	@Test
	void builderWithVarargsShouldStoreToolCallbacks() {
		ToolCallback callback1 = mock(ToolCallback.class);
		ToolCallback callback2 = mock(ToolCallback.class);

		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(callback1, callback2).build();

		assertThat(options.getToolCallbacks()).hasSize(2).containsExactly(callback1, callback2);
	}

	@Test
	void builderShouldRejectNullToolCallbacksVarargs() {
		assertThatThrownBy(() -> ToolCallingChatOptions.builder().toolCallbacks((ToolCallback[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallbacks cannot be null");
	}

	@Test
	void builderShouldStoreToolNames() {
		Set<String> toolNames = Set.of("tool1", "tool2");

		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolNames(toolNames).build();

		assertThat(options.getToolNames()).hasSize(2).containsExactlyInAnyOrderElementsOf(toolNames);
	}

	@Test
	void builderWithVarargsShouldStoreToolNames() {
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolNames("tool1", "tool2").build();

		assertThat(options.getToolNames()).hasSize(2).containsExactlyInAnyOrder("tool1", "tool2");
	}

	@Test
	void builderShouldRejectNullToolNamesVarargs() {
		assertThatThrownBy(() -> ToolCallingChatOptions.builder().toolNames((String[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolNames cannot be null");
	}

	@Test
	void builderShouldStoreToolContext() {
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42);

		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolContext(context).build();

		assertThat(options.getToolContext()).hasSize(2).containsAllEntriesOf(context);
	}

	@Test
	void copyShouldCreateNewInstanceWithSameValues() {
		ToolCallback callback = mock(ToolCallback.class);
		ToolCallingChatOptions original = ToolCallingChatOptions.builder()
			.toolCallbacks(List.of(callback))
			.toolNames(Set.of("tool1"))
			.toolContext(Map.of("key", "value"))
			.internalToolExecutionEnabled(true)
			.model("gpt-4")
			.temperature(0.7)
			.build();

		ToolCallingChatOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original).satisfies(c -> {
			assertThat(c.getToolCallbacks()).isEqualTo(original.getToolCallbacks());
			assertThat(c.getToolNames()).isEqualTo(original.getToolNames());
			assertThat(c.getToolContext()).isEqualTo(original.getToolContext());
			assertThat(c.getInternalToolExecutionEnabled()).isEqualTo(original.getInternalToolExecutionEnabled());
			assertThat(c.getModel()).isEqualTo(original.getModel());
			assertThat(c.getTemperature()).isEqualTo(original.getTemperature());
		});
	}

	@Test
	void gettersShouldReturnImmutableCollections() {
		ToolCallback callback = mock(ToolCallback.class);
		ToolCallingChatOptions options = ToolCallingChatOptions.builder()
			.toolCallbacks(List.of(callback))
			.toolNames(Set.of("tool1"))
			.toolContext(Map.of("key", "value"))
			.build();

		assertThatThrownBy(() -> options.getToolCallbacks().add(mock(ToolCallback.class)))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolNames().add("tool2")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolContext().put("key2", "value2"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void builderShouldCreateOptionsWithAllProperties() {
		ToolCallback callback = mock(ToolCallback.class);
		Map<String, Object> context = Map.of("key", "value");

		ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
			.toolCallbacks(List.of(callback))
			.toolNames(Set.of("tool1"))
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
			assertThat(o.getToolNames()).containsExactly("tool1");
			assertThat(o.getToolContext()).isEqualTo(context);
			assertThat(o.getInternalToolExecutionEnabled()).isTrue();
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
	void defaultConstructorShouldInitializeWithEmptyCollections() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		assertThat(options.getToolCallbacks()).isEmpty();
		assertThat(options.getToolNames()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
	}

	@Test
	void builderShouldHandleEmptyCollections() {
		ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
			.toolCallbacks(List.of())
			.toolNames(Set.of())
			.toolContext(Map.of())
			.build();

		assertThat(options.getToolCallbacks()).isEmpty();
		assertThat(options.getToolNames()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
	}

	@Test
	void builderInternalToolExecutionEnabledShouldAcceptNullValue() {
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();
		assertThat(options.getInternalToolExecutionEnabled()).isTrue();

		// Should be able to set back to null in a new builder
		ToolCallingChatOptions optionsWithNull = ToolCallingChatOptions.builder()
			.internalToolExecutionEnabled(null)
			.build();
		assertThat(optionsWithNull.getInternalToolExecutionEnabled()).isNull();
	}

}
