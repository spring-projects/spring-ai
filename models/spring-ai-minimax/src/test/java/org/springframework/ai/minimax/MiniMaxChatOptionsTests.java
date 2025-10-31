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

package org.springframework.ai.minimax;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.minimax.api.MiniMaxApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MiniMaxChatOptions}.
 *
 * @author Alexandros Pappas
 */
class MiniMaxChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		MiniMaxChatOptions options = MiniMaxChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.N(1)
			.presencePenalty(0.5)
			.responseFormat(new MiniMaxApi.ChatCompletionRequest.ResponseFormat("text"))
			.seed(1)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.maskSensitiveInfo(false)
			.toolChoice("test")
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "maxTokens", "N", "presencePenalty", "responseFormat", "seed",
					"stop", "temperature", "topP", "maskSensitiveInfo", "toolChoice", "internalToolExecutionEnabled",
					"toolContext")
			.containsExactly("test-model", 0.5, 10, 1, 0.5, new MiniMaxApi.ChatCompletionRequest.ResponseFormat("text"),
					1, List.of("test"), 0.6, 0.6, false, "test", true, Map.of("key1", "value1"));
	}

	@Test
	void testCopy() {
		MiniMaxChatOptions original = MiniMaxChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.N(1)
			.presencePenalty(0.5)
			.responseFormat(new MiniMaxApi.ChatCompletionRequest.ResponseFormat("text"))
			.seed(1)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.maskSensitiveInfo(false)
			.toolChoice("test")
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		MiniMaxChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStop()).isNotSameAs(original.getStop());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testNotEquals() {
		MiniMaxChatOptions options1 = MiniMaxChatOptions.builder().model("model1").build();
		MiniMaxChatOptions options2 = MiniMaxChatOptions.builder().model("model2").build();

		assertThat(options1).isNotEqualTo(options2);
	}

	@Test
	void testSettersWithNulls() {
		MiniMaxChatOptions options = new MiniMaxChatOptions();
		options.setModel(null);
		options.setFrequencyPenalty(null);
		options.setMaxTokens(null);
		options.setN(null);
		options.setPresencePenalty(null);
		options.setResponseFormat(null);
		options.setSeed(null);
		options.setStop(null);
		options.setTemperature(null);
		options.setTopP(null);
		options.setMaskSensitiveInfo(null);
		options.setTools(null);
		options.setToolChoice(null);
		options.setInternalToolExecutionEnabled(null);
		options.setToolContext(null);

		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getMaskSensitiveInfo()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
		assertThat(options.getToolContext()).isNull();
	}

	@Test
	void testImmutabilityOfCollections() {
		MiniMaxChatOptions options = MiniMaxChatOptions.builder()
			.stop(new java.util.ArrayList<>(List.of("stop")))
			.tools(new java.util.ArrayList<>(List.of(new MiniMaxApi.FunctionTool(MiniMaxApi.FunctionTool.Type.FUNCTION,
					new MiniMaxApi.FunctionTool.Function("name", "desc", (Map<String, Object>) null)))))
			.toolCallbacks(new java.util.ArrayList<>(List.of()))
			.toolNames(new java.util.HashSet<>(Set.of("tool")))
			.toolContext(new java.util.HashMap<>(Map.of("key", "value")))
			.build();

		assertThatThrownBy(() -> options.getStop().add("another")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getTools().add(null)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolCallbacks().add(null))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolNames().add("another"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> options.getToolContext().put("another", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testSetters() {
		MiniMaxChatOptions options = new MiniMaxChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.5);
		options.setMaxTokens(10);
		options.setN(1);
		options.setPresencePenalty(0.5);
		options.setResponseFormat(new MiniMaxApi.ChatCompletionRequest.ResponseFormat("text"));
		options.setSeed(1);
		options.setStop(List.of("test"));
		options.setTemperature(0.6);
		options.setTopP(0.6);
		options.setMaskSensitiveInfo(false);
		options.setToolChoice("test");
		options.setInternalToolExecutionEnabled(true);
		options.setToolContext(Map.of("key1", "value1"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(10);
		assertThat(options.getN()).isEqualTo(1);
		assertThat(options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(options.getResponseFormat()).isEqualTo(new MiniMaxApi.ChatCompletionRequest.ResponseFormat("text"));
		assertThat(options.getSeed()).isEqualTo(1);
		assertThat(options.getStop()).isEqualTo(List.of("test"));
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.6);
		assertThat(options.getMaskSensitiveInfo()).isEqualTo(false);
		assertThat(options.getToolChoice()).isEqualTo("test");
		assertThat(options.getInternalToolExecutionEnabled()).isEqualTo(true);
		assertThat(options.getToolContext()).isEqualTo(Map.of("key1", "value1"));
	}

	@Test
	void testDefaultValues() {
		MiniMaxChatOptions options = new MiniMaxChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getMaskSensitiveInfo()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
		assertThat(options.getToolContext()).isEqualTo(new java.util.HashMap<>());
	}

}
