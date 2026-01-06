/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.mistralai;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MistralAiChatOptions}.
 *
 * @author Alexandros Pappas
 */
class MistralAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.safePrompt(true)
			.randomSeed(123)
			.stop(List.of("stop1", "stop2"))
			.responseFormat(new ResponseFormat("json_object"))
			.toolChoice(MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO)
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("model", "temperature", "topP", "maxTokens", "safePrompt", "randomSeed", "stop",
					"responseFormat", "toolChoice", "internalToolExecutionEnabled", "toolContext")
			.containsExactly("test-model", 0.7, 0.9, 100, true, 123, List.of("stop1", "stop2"),
					new ResponseFormat("json_object"), MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO, true,
					Map.of("key1", "value1"));
	}

	@Test
	void testBuilderWithEnum() {
		MistralAiChatOptions optionsWithEnum = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MINISTRAL_8B)
			.build();
		assertThat(optionsWithEnum.getModel()).isEqualTo(MistralAiApi.ChatModel.MINISTRAL_8B.getValue());
	}

	@Test
	void testCopy() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.safePrompt(true)
			.randomSeed(123)
			.stop(List.of("stop1", "stop2"))
			.responseFormat(new ResponseFormat("json_object"))
			.toolChoice(MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO)
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		MistralAiChatOptions copiedOptions = options.copy();
		assertThat(copiedOptions).isNotSameAs(options).isEqualTo(options);
		// Ensure deep copy
		assertThat(copiedOptions.getStop()).isNotSameAs(options.getStop());
		assertThat(copiedOptions.getToolContext()).isNotSameAs(options.getToolContext());
	}

	@Test
	void testSetters() {
		ResponseFormat responseFormat = new ResponseFormat("json_object");
		MistralAiChatOptions options = new MistralAiChatOptions();
		options.setModel("test-model");
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setMaxTokens(100);
		options.setSafePrompt(true);
		options.setRandomSeed(123);
		options.setResponseFormat(responseFormat);
		options.setStopSequences(List.of("stop1", "stop2"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getSafePrompt()).isEqualTo(true);
		assertThat(options.getRandomSeed()).isEqualTo(123);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
	}

	@Test
	void testDefaultValues() {
		MistralAiChatOptions options = new MistralAiChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getSafePrompt()).isNull();
		assertThat(options.getRandomSeed()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getResponseFormat()).isNull();
	}

	@Test
	void testBuilderWithEmptyCollections() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.stop(Collections.emptyList())
			.toolContext(Collections.emptyMap())
			.build();

		assertThat(options.getStop()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
	}

	@Test
	void testBuilderWithBoundaryValues() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.temperature(0.0)
			.topP(1.0)
			.maxTokens(1)
			.randomSeed(Integer.MAX_VALUE)
			.build();

		assertThat(options.getTemperature()).isEqualTo(0.0);
		assertThat(options.getTopP()).isEqualTo(1.0);
		assertThat(options.getMaxTokens()).isEqualTo(1);
		assertThat(options.getRandomSeed()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void testBuilderWithSingleElementCollections() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.stop(List.of("single-stop"))
			.toolContext(Map.of("single-key", "single-value"))
			.build();

		assertThat(options.getStop()).hasSize(1).containsExactly("single-stop");
		assertThat(options.getToolContext()).hasSize(1).containsEntry("single-key", "single-value");
	}

	@Test
	void testCopyWithEmptyOptions() {
		MistralAiChatOptions emptyOptions = new MistralAiChatOptions();
		MistralAiChatOptions copiedOptions = emptyOptions.copy();

		assertThat(copiedOptions).isNotSameAs(emptyOptions).isEqualTo(emptyOptions);
		assertThat(copiedOptions.getModel()).isNull();
		assertThat(copiedOptions.getTemperature()).isNull();
	}

	@Test
	void testCopyMutationDoesNotAffectOriginal() {
		MistralAiChatOptions original = MistralAiChatOptions.builder()
			.model("original-model")
			.temperature(0.5)
			.stop(List.of("original-stop"))
			.toolContext(Map.of("original", "value"))
			.build();

		MistralAiChatOptions copy = original.copy();
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
		MistralAiChatOptions options1 = MistralAiChatOptions.builder().model("test-model").temperature(0.7).build();

		MistralAiChatOptions options2 = MistralAiChatOptions.builder().model("test-model").temperature(0.7).build();

		MistralAiChatOptions options3 = MistralAiChatOptions.builder()
			.model("different-model")
			.temperature(0.7)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	void testAllToolChoiceEnumValues() {
		for (MistralAiApi.ChatCompletionRequest.ToolChoice toolChoice : MistralAiApi.ChatCompletionRequest.ToolChoice
			.values()) {

			MistralAiChatOptions options = MistralAiChatOptions.builder().toolChoice(toolChoice).build();

			assertThat(options.getToolChoice()).isEqualTo(toolChoice);
		}
	}

	@Test
	void testResponseFormatTypes() {
		ResponseFormat jsonFormat = new ResponseFormat("json_object");
		ResponseFormat textFormat = new ResponseFormat("text");

		MistralAiChatOptions jsonOptions = MistralAiChatOptions.builder().responseFormat(jsonFormat).build();

		MistralAiChatOptions textOptions = MistralAiChatOptions.builder().responseFormat(textFormat).build();

		assertThat(jsonOptions.getResponseFormat()).isEqualTo(jsonFormat);
		assertThat(textOptions.getResponseFormat()).isEqualTo(textFormat);
		assertThat(jsonOptions.getResponseFormat()).isNotEqualTo(textOptions.getResponseFormat());
	}

	@Test
	void testChainedBuilderMethods() {
		MistralAiChatOptions options = MistralAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.safePrompt(true)
			.randomSeed(123)
			.internalToolExecutionEnabled(false)
			.build();

		// Verify all chained methods worked
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getSafePrompt()).isTrue();
		assertThat(options.getRandomSeed()).isEqualTo(123);
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
	}

	@Test
	void testBuilderAndSetterConsistency() {
		// Build an object using builder
		MistralAiChatOptions builderOptions = MistralAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.topP(0.9)
			.maxTokens(100)
			.build();

		// Create equivalent object using setters
		MistralAiChatOptions setterOptions = new MistralAiChatOptions();
		setterOptions.setModel("test-model");
		setterOptions.setTemperature(0.7);
		setterOptions.setTopP(0.9);
		setterOptions.setMaxTokens(100);

		assertThat(builderOptions).isEqualTo(setterOptions);
	}

}
