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

package org.springframework.ai.cohere.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.cohere.api.CohereApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CohereChatOptions}.
 *
 * @author Ricken Bazolo
 */
class CohereChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		CohereChatOptions options = CohereChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.seed(123)
			.stop(List.of("stop1", "stop2"))
			.toolChoice(CohereApi.ChatCompletionRequest.ToolChoice.REQUIRED)
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("model", "temperature", "topP", "maxTokens", "seed", "stop", "toolChoice",
					"internalToolExecutionEnabled", "toolContext")
			.containsExactly("test-model", 0.7, 0.9, 100, 123, List.of("stop1", "stop2"),
					CohereApi.ChatCompletionRequest.ToolChoice.REQUIRED, true, Map.of("key1", "value1"));
	}

	@Test
	void testBuilderWithEnum() {
		CohereChatOptions optionsWithEnum = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.build();
		assertThat(optionsWithEnum.getModel()).isEqualTo(CohereApi.ChatModel.COMMAND_A_R7B.getValue());
	}

	@Test
	void testCopy() {
		CohereChatOptions options = CohereChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.seed(123)
			.stop(List.of("stop1", "stop2"))
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		CohereChatOptions copiedOptions = options.copy();
		assertThat(copiedOptions).isNotSameAs(options).isEqualTo(options);
		// Ensure deep copy
		assertThat(copiedOptions.getStop()).isNotSameAs(options.getStop());
		assertThat(copiedOptions.getToolContext()).isNotSameAs(options.getToolContext());
	}

	@Test
	void testSetters() {
		CohereChatOptions options = new CohereChatOptions();
		options.setModel("test-model");
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setMaxTokens(100);
		options.setSeed(123);
		options.setStopSequences(List.of("stop1", "stop2"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getSeed()).isEqualTo(123);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
	}

	@Test
	void testDefaultValues() {
		CohereChatOptions options = new CohereChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStopSequences()).isNull();
	}

	@Test
	void testBuilderWithEmptyCollections() {
		CohereChatOptions options = CohereChatOptions.builder()
			.stop(Collections.emptyList())
			.toolContext(Collections.emptyMap())
			.build();

		assertThat(options.getStop()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
	}

	@Test
	void testBuilderWithBoundaryValues() {
		CohereChatOptions options = CohereChatOptions.builder()
			.temperature(0.0)
			.topP(1.0)
			.maxTokens(1)
			.seed(Integer.MAX_VALUE)
			.build();

		assertThat(options.getTemperature()).isEqualTo(0.0);
		assertThat(options.getTopP()).isEqualTo(1.0);
		assertThat(options.getMaxTokens()).isEqualTo(1);
		assertThat(options.getSeed()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void testBuilderWithSingleElementCollections() {
		CohereChatOptions options = CohereChatOptions.builder()
			.stop(List.of("single-stop"))
			.toolContext(Map.of("single-key", "single-value"))
			.build();

		assertThat(options.getStop()).hasSize(1).containsExactly("single-stop");
		assertThat(options.getToolContext()).hasSize(1).containsEntry("single-key", "single-value");
	}

	@Test
	void testCopyWithEmptyOptions() {
		CohereChatOptions emptyOptions = new CohereChatOptions();
		CohereChatOptions copiedOptions = emptyOptions.copy();

		assertThat(copiedOptions).isNotSameAs(emptyOptions).isEqualTo(emptyOptions);
		assertThat(copiedOptions.getModel()).isNull();
		assertThat(copiedOptions.getTemperature()).isNull();
	}

	@Test
	void testCopyMutationDoesNotAffectOriginal() {
		CohereChatOptions original = CohereChatOptions.builder()
			.model("original-model")
			.temperature(0.5)
			.stop(List.of("original-stop"))
			.toolContext(Map.of("original", "value"))
			.build();

		CohereChatOptions copy = original.copy();
		copy.setModel("modified-model");
		copy.setTemperature(0.8);

		// Original should remain unchanged
		assertThat(original.getModel()).isEqualTo("original-model");
		assertThat(original.getTemperature()).isEqualTo(0.5);

		// Copy should have new values
		assertThat(copy.getModel()).isEqualTo("modified-model");
		assertThat(copy.getTemperature()).isEqualTo(0.8);
	}

	@Test
	void testEqualsAndHashCode() {
		CohereChatOptions options1 = CohereChatOptions.builder().model("test-model").temperature(0.7).build();

		CohereChatOptions options2 = CohereChatOptions.builder().model("test-model").temperature(0.7).build();

		CohereChatOptions options3 = CohereChatOptions.builder().model("different-model").temperature(0.7).build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	void testAllToolChoiceEnumValues() {
		for (CohereApi.ChatCompletionRequest.ToolChoice toolChoice : CohereApi.ChatCompletionRequest.ToolChoice
			.values()) {

			CohereChatOptions options = CohereChatOptions.builder().toolChoice(toolChoice).build();

			assertThat(options.getToolChoice()).isEqualTo(toolChoice);
		}
	}

	@Test
	void testChainedBuilderMethods() {
		CohereChatOptions options = CohereChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.seed(123)
			.internalToolExecutionEnabled(false)
			.build();

		// Verify all chained methods worked
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getSeed()).isEqualTo(123);
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
	}

	@Test
	void testBuilderAndSetterConsistency() {
		// Build an object using builder
		CohereChatOptions builderOptions = CohereChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.build();

		// Create equivalent object using setters
		CohereChatOptions setterOptions = new CohereChatOptions();
		setterOptions.setModel("test-model");
		setterOptions.setTemperature(0.7);
		setterOptions.setTopP(0.9);
		setterOptions.setMaxTokens(100);

		assertThat(builderOptions).isEqualTo(setterOptions);
	}

}
