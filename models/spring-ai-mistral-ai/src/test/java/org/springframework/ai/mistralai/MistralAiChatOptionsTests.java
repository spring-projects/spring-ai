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

package org.springframework.ai.mistralai;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;

import org.springframework.ai.mistralai.api.MistralAiApi;

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
			.proxyToolCalls(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("model", "temperature", "topP", "maxTokens", "safePrompt", "randomSeed", "stop",
					"responseFormat", "toolChoice", "proxyToolCalls", "toolContext")
			.containsExactly("test-model", 0.7, 0.9, 100, true, 123, List.of("stop1", "stop2"),
					new ResponseFormat("json_object"), MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO, true,
					Map.of("key1", "value1"));
	}

	@Test
	void testBuilderWithEnum() {
		MistralAiChatOptions optionsWithEnum = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.OPEN_MISTRAL_7B)
			.build();
		assertThat(optionsWithEnum.getModel()).isEqualTo(MistralAiApi.ChatModel.OPEN_MISTRAL_7B.getValue());
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
			.proxyToolCalls(true)
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

}
