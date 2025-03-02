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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.AudioParameters.Voice.ALLOY;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.AudioParameters;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.StreamOptions;
import org.springframework.ai.openai.api.ResponseFormat;

/**
 * Tests for {@link OpenAiChatOptions}.
 *
 * @author Alexandros Pappas
 */
class OpenAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);
		logitBias.put("token2", -1);

		List<String> outputModalities = List.of("text", "audio");
		AudioParameters outputAudio = new AudioParameters(ALLOY, AudioParameters.AudioResponseFormat.MP3);
		ResponseFormat responseFormat = new ResponseFormat();
		StreamOptions streamOptions = StreamOptions.INCLUDE_USAGE;
		List<String> stopSequences = List.of("stop1", "stop2");
		List<OpenAiApi.FunctionTool> tools = new ArrayList<>();
		Object toolChoice = "auto";
		Map<String, String> metadata = Map.of("key1", "value1");
		Map<String, Object> toolContext = Map.of("keyA", "valueA");

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxTokens(100)
			.maxCompletionTokens(50)
			.N(2)
			.outputModalities(outputModalities)
			.outputAudio(outputAudio)
			.presencePenalty(0.8)
			.responseFormat(responseFormat)
			.streamUsage(true)
			.seed(12345)
			.stop(stopSequences)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.toolChoice(toolChoice)
			.user("test-user")
			.parallelToolCalls(true)
			.store(false)
			.metadata(metadata)
			.reasoningEffort("medium")
			.proxyToolCalls(false)
			.httpHeaders(Map.of("header1", "value1"))
			.toolContext(toolContext)
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "logitBias", "logprobs", "topLogprobs", "maxTokens",
					"maxCompletionTokens", "n", "outputModalities", "outputAudio", "presencePenalty", "responseFormat",
					"streamOptions", "seed", "stop", "temperature", "topP", "tools", "toolChoice", "user",
					"parallelToolCalls", "store", "metadata", "reasoningEffort", "proxyToolCalls", "httpHeaders",
					"toolContext")
			.containsExactly("test-model", 0.5, logitBias, true, 5, 100, 50, 2, outputModalities, outputAudio, 0.8,
					responseFormat, streamOptions, 12345, stopSequences, 0.7, 0.9, tools, toolChoice, "test-user", true,
					false, metadata, "medium", false, Map.of("header1", "value1"), toolContext);

		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getStreamOptions()).isEqualTo(StreamOptions.INCLUDE_USAGE);

	}

	@Test
	void testCopy() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> outputModalities = List.of("text");
		AudioParameters outputAudio = new AudioParameters(ALLOY, AudioParameters.AudioResponseFormat.MP3);
		ResponseFormat responseFormat = new ResponseFormat();

		List<String> stopSequences = List.of("stop1");
		List<OpenAiApi.FunctionTool> tools = new ArrayList<>();
		Object toolChoice = "none";
		Map<String, String> metadata = Map.of("key1", "value1");

		OpenAiChatOptions originalOptions = OpenAiChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxTokens(100)
			.maxCompletionTokens(50)
			.N(2)
			.outputModalities(outputModalities)
			.outputAudio(outputAudio)
			.presencePenalty(0.8)
			.responseFormat(responseFormat)
			.streamUsage(false)
			.seed(12345)
			.stop(stopSequences)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.toolChoice(toolChoice)
			.user("test-user")
			.parallelToolCalls(false)
			.store(true)
			.metadata(metadata)
			.reasoningEffort("low")
			.proxyToolCalls(true)
			.httpHeaders(Map.of("header1", "value1"))
			.build();

		OpenAiChatOptions copiedOptions = originalOptions.copy();
		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testSetters() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> outputModalities = List.of("audio");
		AudioParameters outputAudio = new AudioParameters(ALLOY, AudioParameters.AudioResponseFormat.MP3);
		ResponseFormat responseFormat = new ResponseFormat();

		StreamOptions streamOptions = StreamOptions.INCLUDE_USAGE;
		List<String> stopSequences = List.of("stop1", "stop2");
		List<OpenAiApi.FunctionTool> tools = new ArrayList<>();
		Object toolChoice = "auto";
		Map<String, String> metadata = Map.of("key2", "value2");

		OpenAiChatOptions options = new OpenAiChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.5);
		options.setLogitBias(logitBias);
		options.setLogprobs(true);
		options.setTopLogprobs(5);
		options.setMaxTokens(100);
		options.setMaxCompletionTokens(50);
		options.setN(2);
		options.setOutputModalities(outputModalities);
		options.setOutputAudio(outputAudio);
		options.setPresencePenalty(0.8);
		options.setResponseFormat(responseFormat);
		options.setStreamOptions(streamOptions);
		options.setSeed(12345);
		options.setStop(stopSequences);
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setTools(tools);
		options.setToolChoice(toolChoice);
		options.setUser("test-user");
		options.setParallelToolCalls(true);
		options.setStore(false);
		options.setMetadata(metadata);
		options.setReasoningEffort("high");
		options.setProxyToolCalls(false);
		options.setHttpHeaders(Map.of("header2", "value2"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getLogitBias()).isEqualTo(logitBias);
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(5);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getMaxCompletionTokens()).isEqualTo(50);
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getOutputModalities()).isEqualTo(outputModalities);
		assertThat(options.getOutputAudio()).isEqualTo(outputAudio);
		assertThat(options.getPresencePenalty()).isEqualTo(0.8);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(options.getStreamOptions()).isEqualTo(streamOptions);
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getStop()).isEqualTo(stopSequences);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getTools()).isEqualTo(tools);
		assertThat(options.getToolChoice()).isEqualTo(toolChoice);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getParallelToolCalls()).isTrue();
		assertThat(options.getStore()).isFalse();
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getReasoningEffort()).isEqualTo("high");
		assertThat(options.getProxyToolCalls()).isFalse();
		assertThat(options.getHttpHeaders()).isEqualTo(Map.of("header2", "value2"));
		assertThat(options.getStreamUsage()).isTrue();
		options.setStreamUsage(false);
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStreamOptions()).isNull();
		options.setStopSequences(List.of("s1", "s2"));
		assertThat(options.getStopSequences()).isEqualTo(List.of("s1", "s2"));
		assertThat(options.getStop()).isEqualTo(List.of("s1", "s2"));
	}

	@Test
	void testDefaultValues() {
		OpenAiChatOptions options = new OpenAiChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getLogprobs()).isNull();
		assertThat(options.getTopLogprobs()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getOutputModalities()).isNull();
		assertThat(options.getOutputAudio()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getStreamOptions()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getParallelToolCalls()).isNull();
		assertThat(options.getStore()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getReasoningEffort()).isNull();
		assertThat(options.getFunctionCallbacks()).isNotNull().isEmpty();
		assertThat(options.getFunctions()).isNotNull().isEmpty();
		assertThat(options.getProxyToolCalls()).isNull();
		assertThat(options.getHttpHeaders()).isNotNull().isEmpty();
		assertThat(options.getToolContext()).isEqualTo(new HashMap<>());
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStopSequences()).isNull();
	}

}
