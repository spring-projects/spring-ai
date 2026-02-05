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

package org.springframework.ai.anthropicsdk;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAuto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnthropicSdkChatOptions}. Focuses on critical behaviors: builder,
 * copy, merge, equals/hashCode, and validation.
 *
 * @author Soby Chacko
 */
class AnthropicSdkChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		Metadata metadata = Metadata.builder().userId("userId_123").build();
		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(metadata)
			.baseUrl("https://custom.api.com")
			.timeout(Duration.ofSeconds(120))
			.maxRetries(5)
			.toolChoice(ToolChoice.ofAuto(ToolChoiceAuto.builder().build()))
			.disableParallelToolUse(true)
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key", "value"))
			.internalToolExecutionEnabled(true)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getStopSequences()).containsExactly("stop1", "stop2");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getBaseUrl()).isEqualTo("https://custom.api.com");
		assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(120));
		assertThat(options.getMaxRetries()).isEqualTo(5);
		assertThat(options.getToolChoice()).isNotNull();
		assertThat(options.getDisableParallelToolUse()).isTrue();
		assertThat(options.getToolNames()).containsExactlyInAnyOrder("tool1", "tool2");
		assertThat(options.getToolContext()).containsEntry("key", "value");
		assertThat(options.getInternalToolExecutionEnabled()).isTrue();
	}

	@Test
	void testBuilderWithModelEnum() {
		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514)
			.build();

		assertThat(options.getModel()).isEqualTo("claude-sonnet-4-20250514");
	}

	@Test
	void testCopyCreatesIndependentInstance() {
		Metadata metadata = Metadata.builder().userId("userId_123").build();
		List<String> mutableStops = new ArrayList<>(List.of("stop1", "stop2"));
		Map<String, Object> mutableContext = new HashMap<>(Map.of("key1", "value1"));

		AnthropicSdkChatOptions original = AnthropicSdkChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(mutableStops)
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(metadata)
			.toolContext(mutableContext)
			.disableParallelToolUse(true)
			.build();

		AnthropicSdkChatOptions copied = original.copy();

		// Verify copied is equal but not same instance
		assertThat(copied).isNotSameAs(original);
		assertThat(copied).isEqualTo(original);

		// Verify collections are deep copied
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());

		// Modify copy and verify original is unchanged
		copied.setModel("modified-model");
		copied.setMaxTokens(200);
		assertThat(original.getModel()).isEqualTo("test-model");
		assertThat(original.getMaxTokens()).isEqualTo(100);

		// Modify original collections and verify copy is unchanged
		mutableStops.add("stop3");
		mutableContext.put("key2", "value2");
		assertThat(copied.getStopSequences()).hasSize(2);
		assertThat(copied.getToolContext()).hasSize(1);
	}

	@Test
	void testMergeOverridesOnlyNonNullValues() {
		AnthropicSdkChatOptions base = AnthropicSdkChatOptions.builder()
			.model("base-model")
			.maxTokens(100)
			.temperature(0.5)
			.topP(0.8)
			.baseUrl("https://base.api.com")
			.timeout(Duration.ofSeconds(60))
			.build();

		AnthropicSdkChatOptions override = AnthropicSdkChatOptions.builder()
			.model("override-model")
			.topK(40)
			// maxTokens, temperature, topP, baseUrl, timeout are null
			.build();

		AnthropicSdkChatOptions merged = AnthropicSdkChatOptions.builder().from(base).merge(override).build();

		// Override values take precedence
		assertThat(merged.getModel()).isEqualTo("override-model");
		assertThat(merged.getTopK()).isEqualTo(40);

		// Base values preserved when override is null
		assertThat(merged.getMaxTokens()).isEqualTo(100);
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		assertThat(merged.getTopP()).isEqualTo(0.8);
		assertThat(merged.getBaseUrl()).isEqualTo("https://base.api.com");
		assertThat(merged.getTimeout()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void testMergeWithCollections() {
		AnthropicSdkChatOptions base = AnthropicSdkChatOptions.builder()
			.stopSequences(List.of("base-stop"))
			.toolNames(Set.of("base-tool"))
			.toolContext(Map.of("base-key", "base-value"))
			.build();

		AnthropicSdkChatOptions override = AnthropicSdkChatOptions.builder()
			.stopSequences(List.of("override-stop1", "override-stop2"))
			.toolNames(Set.of("override-tool"))
			// toolContext is empty, should not override
			.build();

		AnthropicSdkChatOptions merged = AnthropicSdkChatOptions.builder().from(base).merge(override).build();

		// Non-empty collections from override take precedence
		assertThat(merged.getStopSequences()).containsExactly("override-stop1", "override-stop2");
		assertThat(merged.getToolNames()).containsExactly("override-tool");
		// Empty collections don't override
		assertThat(merged.getToolContext()).containsEntry("base-key", "base-value");
	}

	@Test
	void testEqualsAndHashCode() {
		AnthropicSdkChatOptions options1 = AnthropicSdkChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		AnthropicSdkChatOptions options2 = AnthropicSdkChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		AnthropicSdkChatOptions options3 = AnthropicSdkChatOptions.builder()
			.model("different-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		// Equal objects
		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		// Different objects
		assertThat(options1).isNotEqualTo(options3);

		// Null and different type
		assertThat(options1).isNotEqualTo(null);
		assertThat(options1).isNotEqualTo("not an options object");
	}

	@Test
	void testToolCallbacksValidationRejectsNull() {
		AnthropicSdkChatOptions options = new AnthropicSdkChatOptions();

		assertThatThrownBy(() -> options.setToolCallbacks(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallbacks cannot be null");
	}

	@Test
	void testToolNamesValidationRejectsNull() {
		AnthropicSdkChatOptions options = new AnthropicSdkChatOptions();

		assertThatThrownBy(() -> options.setToolNames(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot be null");
	}

	@Test
	void testDefaultConstants() {
		assertThat(AnthropicSdkChatOptions.DEFAULT_MODEL).isEqualTo("claude-sonnet-4-20250514");
		assertThat(AnthropicSdkChatOptions.DEFAULT_MAX_TOKENS).isEqualTo(4096);
	}

	@Test
	void testUnsupportedPenaltyMethodsReturnNull() {
		AnthropicSdkChatOptions options = new AnthropicSdkChatOptions();

		// Anthropic API does not support these OpenAI-specific parameters
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
	}

}
