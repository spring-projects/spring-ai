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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.AudioParameters;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.StreamOptions;
import org.springframework.ai.openai.api.OpenAiApi.ServiceTier;
import org.springframework.ai.openai.api.ResponseFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.AudioParameters.Voice.ALLOY;

/**
 * Tests for {@link OpenAiChatOptions}.
 *
 * @author Alexandros Pappas
 * @author Filip Hrisafov
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
			.internalToolExecutionEnabled(false)
			.httpHeaders(Map.of("header1", "value1"))
			.toolContext(toolContext)
			.serviceTier(ServiceTier.PRIORITY)
			.promptCacheKey("test-cache-key")
			.safetyIdentifier("test-safety-id")
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "logitBias", "logprobs", "topLogprobs", "maxTokens",
					"maxCompletionTokens", "n", "outputModalities", "outputAudio", "presencePenalty", "responseFormat",
					"streamOptions", "seed", "stop", "temperature", "topP", "tools", "toolChoice", "user",
					"parallelToolCalls", "store", "metadata", "reasoningEffort", "internalToolExecutionEnabled",
					"httpHeaders", "toolContext", "serviceTier", "promptCacheKey", "safetyIdentifier")
			.containsExactly("test-model", 0.5, logitBias, true, 5, null, 50, 2, outputModalities, outputAudio, 0.8,
					responseFormat, streamOptions, 12345, stopSequences, 0.7, 0.9, tools, toolChoice, "test-user", true,
					false, metadata, "medium", false, Map.of("header1", "value1"), toolContext,
					ServiceTier.PRIORITY.getValue(), "test-cache-key", "test-safety-id");

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
			.maxCompletionTokens(50) // Only set maxCompletionTokens to avoid validation
										// conflict
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
			.internalToolExecutionEnabled(true)
			.httpHeaders(Map.of("header1", "value1"))
			.serviceTier(ServiceTier.DEFAULT)
			.promptCacheKey("copy-test-cache")
			.safetyIdentifier("copy-test-safety")
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
		options.setInternalToolExecutionEnabled(false);
		options.setHttpHeaders(Map.of("header2", "value2"));
		options.setServiceTier(ServiceTier.DEFAULT.getValue());

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
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getHttpHeaders()).isEqualTo(Map.of("header2", "value2"));
		assertThat(options.getStreamUsage()).isTrue();
		options.setStreamUsage(false);
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStreamOptions()).isNull();
		options.setStopSequences(List.of("s1", "s2"));
		assertThat(options.getStopSequences()).isEqualTo(List.of("s1", "s2"));
		assertThat(options.getStop()).isEqualTo(List.of("s1", "s2"));
		assertThat(options.getServiceTier()).isEqualTo("default");
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
		assertThat(options.getToolCallbacks()).isNotNull().isEmpty();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
		assertThat(options.getHttpHeaders()).isNotNull().isEmpty();
		assertThat(options.getToolContext()).isEqualTo(new HashMap<>());
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getServiceTier()).isNull();
		assertThat(options.getOutputSchema()).isNull();
	}

	@Test
	void testFromOptions_webSearchOptions() {
		var chatOptions = OpenAiChatOptions.builder()
			.webSearchOptions(new OpenAiApi.ChatCompletionRequest.WebSearchOptions(
					org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize.MEDIUM,
					new OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation("type",
							new OpenAiApi.ChatCompletionRequest.WebSearchOptions.UserLocation.Approximate("beijing",
									"china", "region", "UTC+8"))))
			.build();
		var target = OpenAiChatOptions.fromOptions(chatOptions);
		assertThat(target.getWebSearchOptions()).isNotNull();
		assertThat(target.getWebSearchOptions().searchContextSize()).isEqualTo(
				org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize.MEDIUM);
		assertThat(target.getWebSearchOptions().userLocation()).isNotNull();
		assertThat(target.getWebSearchOptions().userLocation().type()).isEqualTo("type");
		assertThat(target.getWebSearchOptions().userLocation().approximate()).isNotNull();
		assertThat(target.getWebSearchOptions().userLocation().approximate().city()).isEqualTo("beijing");
		assertThat(target.getWebSearchOptions().userLocation().approximate().country()).isEqualTo("china");
		assertThat(target.getWebSearchOptions().userLocation().approximate().region()).isEqualTo("region");
		assertThat(target.getWebSearchOptions().userLocation().approximate().timezone()).isEqualTo("UTC+8");
	}

	@Test
	@SuppressWarnings("SelfAssertion")
	void testEqualsAndHashCode() {
		OpenAiChatOptions options1 = OpenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		OpenAiChatOptions options2 = OpenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		OpenAiChatOptions options3 = OpenAiChatOptions.builder()
			.model("different-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		// Test equals
		assertThat(options1).isEqualTo(options2);
		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1).isNotEqualTo(null);
		assertThat(options1).isEqualTo(options1);

		// Test hashCode
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	void testBuilderWithNullValues() {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.temperature(null)
			.logitBias(null)
			.stop(null)
			.tools(null)
			.metadata(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getMetadata()).isNull();
	}

	@Test
	void testBuilderChaining() {
		OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

		OpenAiChatOptions.Builder result = builder.model("test-model").temperature(0.7).maxTokens(100);

		assertThat(result).isSameAs(builder);

		OpenAiChatOptions options = result.build();
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testNullAndEmptyCollections() {
		OpenAiChatOptions options = new OpenAiChatOptions();

		// Test setting null collections
		options.setLogitBias(null);
		options.setStop(null);
		options.setTools(null);
		options.setMetadata(null);
		options.setOutputModalities(null);

		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getOutputModalities()).isNull();

		// Test setting empty collections
		options.setLogitBias(new HashMap<>());
		options.setStop(new ArrayList<>());
		options.setTools(new ArrayList<>());
		options.setMetadata(new HashMap<>());
		options.setOutputModalities(new ArrayList<>());

		assertThat(options.getLogitBias()).isEmpty();
		assertThat(options.getStop()).isEmpty();
		assertThat(options.getTools()).isEmpty();
		assertThat(options.getMetadata()).isEmpty();
		assertThat(options.getOutputModalities()).isEmpty();
	}

	@Test
	void testStreamUsageStreamOptionsInteraction() {
		OpenAiChatOptions options = new OpenAiChatOptions();

		// Initially false
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStreamOptions()).isNull();

		// Setting streamUsage to true should set streamOptions
		options.setStreamUsage(true);
		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getStreamOptions()).isEqualTo(StreamOptions.INCLUDE_USAGE);

		// Setting streamUsage to false should clear streamOptions
		options.setStreamUsage(false);
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStreamOptions()).isNull();

		// Setting streamOptions directly should update streamUsage
		options.setStreamOptions(StreamOptions.INCLUDE_USAGE);
		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getStreamOptions()).isEqualTo(StreamOptions.INCLUDE_USAGE);

		// Setting streamOptions to null should set streamUsage to false
		options.setStreamOptions(null);
		assertThat(options.getStreamUsage()).isFalse();
		assertThat(options.getStreamOptions()).isNull();
	}

	@Test
	void testStopSequencesAlias() {
		OpenAiChatOptions options = new OpenAiChatOptions();
		List<String> stopSequences = List.of("stop1", "stop2");

		// Setting stopSequences should also set stop
		options.setStopSequences(stopSequences);
		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
		assertThat(options.getStop()).isEqualTo(stopSequences);

		// Setting stop should also update stopSequences
		List<String> newStop = List.of("stop3", "stop4");
		options.setStop(newStop);
		assertThat(options.getStop()).isEqualTo(newStop);
		assertThat(options.getStopSequences()).isEqualTo(newStop);
	}

	@Test
	void testFromOptionsWithWebSearchOptionsNull() {
		OpenAiChatOptions source = OpenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.webSearchOptions(null)
			.build();

		OpenAiChatOptions result = OpenAiChatOptions.fromOptions(source);
		assertThat(result.getModel()).isEqualTo("test-model");
		assertThat(result.getTemperature()).isEqualTo(0.7);
		assertThat(result.getWebSearchOptions()).isNull();
	}

	@Test
	void testCopyChangeIndependence() {
		OpenAiChatOptions original = OpenAiChatOptions.builder().model("original-model").temperature(0.5).build();

		OpenAiChatOptions copied = original.copy();

		// Modify original
		original.setModel("modified-model");
		original.setTemperature(0.9);

		// Verify copy is unchanged
		assertThat(copied.getModel()).isEqualTo("original-model");
		assertThat(copied.getTemperature()).isEqualTo(0.5);
	}

	@Test
	void testMaxTokensMutualExclusivityValidation() {
		// Test that setting maxTokens clears maxCompletionTokens
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.maxCompletionTokens(100)
			.maxTokens(50) // This should clear maxCompletionTokens
			.build();

		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testMaxCompletionTokensMutualExclusivityValidation() {
		// Test that setting maxCompletionTokens clears maxTokens
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.maxTokens(50)
			.maxCompletionTokens(100) // This should clear maxTokens
			.build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxTokensWithNullDoesNotClearMaxCompletionTokens() {
		// Test that setting maxTokens to null doesn't trigger validation
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.maxCompletionTokens(100)
			.maxTokens(null) // This should not clear maxCompletionTokens
			.build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxCompletionTokensWithNullDoesNotClearMaxTokens() {
		// Test that setting maxCompletionTokens to null doesn't trigger validation
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.maxTokens(50)
			.maxCompletionTokens(null) // This should not clear maxTokens
			.build();

		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxTokens() {
		// Test that we can set only maxTokens without issues
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxTokens(100).build();

		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxCompletionTokens() {
		// Test that we can set only maxCompletionTokens without issues
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxCompletionTokens(150).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(150);
	}

	@Test
	void testSettersMutualExclusivityNotEnforced() {
		// Test that direct setters do NOT enforce mutual exclusivity (only builder does)
		OpenAiChatOptions options = new OpenAiChatOptions();
		options.setMaxTokens(50);
		options.setMaxCompletionTokens(100);

		// Both should be set when using setters directly
		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testSetOutputSchema() {
		OpenAiChatOptions options = new OpenAiChatOptions();
		// language=JSON
		String schema = """
				{
					"type": "object",
					"properties": {
						"name": {
							"type": "string"
						}
					}
				}
				""";

		options.setOutputSchema(schema);

		assertThat(options.getResponseFormat()).isNotNull();
		assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(options.getResponseFormat().getJsonSchema()).isNotNull();
		assertThat(options.getResponseFormat().getJsonSchema().getSchema()).containsKey("type");
		assertThat(options.getResponseFormat().getJsonSchema().getStrict()).isTrue();
		assertThat(options.getResponseFormat().getJsonSchema().getName()).isEqualTo("custom_schema");
		assertThat(options.getResponseFormat().getSchema()).isEqualTo(schema);
		assertThat(options.getOutputSchema()).isEqualTo(schema);
	}

	@Test
	void testGetOutputSchemaWithResponseFormatJsonSchema() {
		OpenAiChatOptions options = new OpenAiChatOptions();
		// language=JSON
		String schema = """
				{
					"type": "object",
					"properties": {
						"name": {
							"type": "string"
						}
					}
				}
				""";

		options.setResponseFormat(ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(ResponseFormat.JsonSchema.builder().strict(true).schema(schema).build())
			.build());

		assertThat(options.getResponseFormat()).isNotNull();
		assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(options.getResponseFormat().getJsonSchema()).isNotNull();
		assertThat(options.getResponseFormat().getJsonSchema().getSchema()).containsKey("type");
		assertThat(options.getResponseFormat().getJsonSchema().getStrict()).isTrue();
		assertThat(options.getResponseFormat().getJsonSchema().getName()).isEqualTo("custom_schema");
		assertThat(options.getResponseFormat().getSchema()).isNull();
		JsonAssertions.assertThatJson(options.getOutputSchema()).isEqualTo("""
				{
					"type": "object",
					"properties": {
						"name": {
							"type": "string"
						}
					}
				}
				""");
	}

}
