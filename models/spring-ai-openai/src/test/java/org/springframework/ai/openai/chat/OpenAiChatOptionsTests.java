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

package org.springframework.ai.openai.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.Builder;
import org.springframework.ai.openai.OpenAiChatOptions.StreamOptions;
import org.springframework.ai.test.options.AbstractChatOptionsTests;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenAiChatOptions}.
 *
 * @author Julien Dubois
 */
public class OpenAiChatOptionsTests extends AbstractChatOptionsTests<OpenAiChatOptions, Builder> {

	@Override
	protected Class<OpenAiChatOptions> getConcreteOptionsClass() {
		return OpenAiChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return OpenAiChatOptions.builder();
	}

	@Test
	void testBuilderWithAllFields() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);
		logitBias.put("token2", -1);

		List<String> stop = List.of("stop1", "stop2");
		Map<String, String> metadata = Map.of("key1", "value1");
		Map<String, Object> toolContext = Map.of("keyA", "valueA");
		Map<String, String> customHeaders = Map.of("header1", "value1");
		Map<String, Object> extraBody = Map.of("top_k", 50, "repetition_penalty", 1.2);

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("test-model")
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxTokens(100)
			.maxCompletionTokens(50)
			.N(2)
			.presencePenalty(0.8)
			.streamOptions(StreamOptions.builder().includeUsage(true).build())
			.seed(12345)
			.stop(stop)
			.temperature(0.7)
			.topP(0.9)
			.user("test-user")
			.parallelToolCalls(true)
			.store(false)
			.metadata(metadata)
			.reasoningEffort("medium")
			.verbosity("low")
			.serviceTier("auto")
			.internalToolExecutionEnabled(false)
			.customHeaders(customHeaders)
			.toolContext(toolContext)
			.extraBody(extraBody)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getDeploymentName()).isEqualTo("test-deployment");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getLogitBias()).isEqualTo(logitBias);
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(5);
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(50);
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getPresencePenalty()).isEqualTo(0.8);
		assertThat(options.getStreamOptions().includeUsage()).isTrue();
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getStop()).isEqualTo(stop);
		assertThat(options.getStopSequences()).isEqualTo(stop);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getParallelToolCalls()).isTrue();
		assertThat(options.getStore()).isFalse();
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getReasoningEffort()).isEqualTo("medium");
		assertThat(options.getVerbosity()).isEqualTo("low");
		assertThat(options.getServiceTier()).isEqualTo("auto");
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getCustomHeaders()).isEqualTo(customHeaders);
		assertThat(options.getToolContext()).isEqualTo(toolContext);
		assertThat(options.getExtraBody()).isEqualTo(extraBody);
	}

	@Test
	void testCopy() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> stop = List.of("stop1");
		Map<String, String> metadata = Map.of("key1", "value1");

		OpenAiChatOptions originalOptions = OpenAiChatOptions.builder()
			.model("test-model")
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxCompletionTokens(50)
			.N(2)
			.presencePenalty(0.8)
			.streamOptions(StreamOptions.builder().includeUsage(false).build())
			.seed(12345)
			.stop(stop)
			.temperature(0.7)
			.topP(0.9)
			.user("test-user")
			.parallelToolCalls(false)
			.store(true)
			.metadata(metadata)
			.reasoningEffort("low")
			.verbosity("high")
			.serviceTier("default")
			.internalToolExecutionEnabled(true)
			.customHeaders(Map.of("header1", "value1"))
			.build();

		OpenAiChatOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
		// Verify collections are copied
		assertThat(copiedOptions.getStop()).isNotSameAs(originalOptions.getStop());
		assertThat(copiedOptions.getCustomHeaders()).isNotSameAs(originalOptions.getCustomHeaders());
		assertThat(copiedOptions.getToolCallbacks()).isNotSameAs(originalOptions.getToolCallbacks());
		assertThat(copiedOptions.getToolNames()).isNotSameAs(originalOptions.getToolNames());
		assertThat(copiedOptions.getToolContext()).isNotSameAs(originalOptions.getToolContext());
	}

	@Test
	void testSetters() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> stop = List.of("stop1", "stop2");
		Map<String, String> metadata = Map.of("key2", "value2");

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("test-model")
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxCompletionTokens(50)
			.n(2)
			.presencePenalty(0.8)
			.streamOptions(StreamOptions.builder().includeUsage(true).build())
			.seed(12345)
			.stopSequences(stop)
			.temperature(0.7)
			.topP(0.9)
			.user("test-user")
			.parallelToolCalls(true)
			.store(false)
			.metadata(metadata)
			.reasoningEffort("high")
			.verbosity("medium")
			.serviceTier("auto")
			.internalToolExecutionEnabled(false)
			.customHeaders(Map.of("header2", "value2"))
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getDeploymentName()).isEqualTo("test-deployment");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getLogitBias()).isEqualTo(logitBias);
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(5);
		assertThat(options.getMaxCompletionTokens()).isEqualTo(50);
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getPresencePenalty()).isEqualTo(0.8);
		assertThat(options.getStreamOptions().includeUsage()).isTrue();
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getStop()).isEqualTo(stop);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getParallelToolCalls()).isTrue();
		assertThat(options.getStore()).isFalse();
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getReasoningEffort()).isEqualTo("high");
		assertThat(options.getVerbosity()).isEqualTo("medium");
		assertThat(options.getServiceTier()).isEqualTo("auto");
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getCustomHeaders()).isEqualTo(Map.of("header2", "value2"));
	}

	@Test
	void testDefaultValues() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getDeploymentName()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getLogprobs()).isNull();
		assertThat(options.getTopLogprobs()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getOutputAudio()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getStreamOptions()).isNull();
		assertThat(options.getStreamOptions()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getParallelToolCalls()).isNull();
		assertThat(options.getStore()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getReasoningEffort()).isNull();
		assertThat(options.getVerbosity()).isNull();
		assertThat(options.getServiceTier()).isNull();
		assertThat(options.getToolCallbacks()).isNotNull().isEmpty();
		assertThat(options.getToolNames()).isNotNull().isEmpty();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
		assertThat(options.getCustomHeaders()).isNotNull().isEmpty();
		assertThat(options.getToolContext()).isNotNull().isEmpty();
		assertThat(options.getOutputSchema()).isNull();
	}

	@Test
	void testEqualsAndHashCode() {
		OpenAiChatOptions options1 = OpenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.extraBody(Map.of("key1", "value1"))
			.build();

		OpenAiChatOptions options2 = OpenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.extraBody(Map.of("key1", "value1"))
			.build();

		OpenAiChatOptions options3 = OpenAiChatOptions.builder()
			.model("different-model")
			.temperature(0.7)
			.maxTokens(100)
			.extraBody(Map.of("key1", "value2"))
			.build();

		// Test equals
		assertThat(options1).isEqualTo(options2);
		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1).isNotEqualTo(null);

		// Test hashCode
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testBuilderWithNullValues() {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.temperature(null)
			.logitBias(null)
			.stop(null)
			.metadata(null)
			.extraBody(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getExtraBody()).isNull();
	}

	@Test
	void testBuilderChaining() {
		Builder builder = OpenAiChatOptions.builder();

		Builder result = builder.model("test-model").temperature(0.7).maxTokens(100);

		assertThat(result).isSameAs(builder);

		OpenAiChatOptions options = result.build();
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
	}

	@Test
	void testNullAndEmptyCollections() {
		// Test setting null collections
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.logitBias(null)
			.stopSequences(null)
			.metadata(null)
			.build();

		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getMetadata()).isNull();

		// Test setting empty collections
		options = options.mutate()
			.logitBias(new java.util.HashMap<>())
			.stopSequences(new java.util.ArrayList<>())
			.metadata(new java.util.HashMap<>())
			.customHeaders(new java.util.HashMap<>())
			.build();

		assertThat(options.getLogitBias()).isEmpty();
		assertThat(options.getStop()).isEmpty();
		assertThat(options.getMetadata()).isEmpty();
		assertThat(options.getCustomHeaders()).isEmpty();
	}

	@Test
	void testStopSequencesAlias() {
		List<String> stopSequences = List.of("stop1", "stop2");

		// Setting stopSequences should also set stop
		OpenAiChatOptions options = OpenAiChatOptions.builder().stopSequences(stopSequences).build();
		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
		assertThat(options.getStop()).isEqualTo(stopSequences);

		// Setting stop should also update stopSequences
		List<String> newStop = List.of("stop3", "stop4");
		options = options.mutate().stopSequences(newStop).build();
		assertThat(options.getStop()).isEqualTo(newStop);
		assertThat(options.getStopSequences()).isEqualTo(newStop);
	}

	@Test
	void testCopyChangeIndependence() {
		OpenAiChatOptions original = OpenAiChatOptions.builder().model("original-model").temperature(0.5).build();

		OpenAiChatOptions copied = original.copy();

		// Modify original
		original = original.mutate().model("modified-model").temperature(0.9).build();

		// Verify copy is unchanged
		assertThat(copied.getModel()).isEqualTo("original-model");
		assertThat(copied.getTemperature()).isEqualTo(0.5);
	}

	@Test
	void testMaxTokensIsDeprectaed() {
		// Test that setting maxCompletionTokens takes precedence over maxTokens in
		// builder
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxCompletionTokens(100).maxTokens(50).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxCompletionTokensMutualExclusivityValidation() {
		// Test that setting maxCompletionTokens clears maxTokens in builder
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxTokens(50).maxCompletionTokens(100).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxTokensWithNullDoesNotClearMaxCompletionTokens() {
		// Test that setting maxTokens to null doesn't trigger validation
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxCompletionTokens(100).maxTokens(null).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxCompletionTokensWithNullDoesNotClearMaxTokens() {
		// Test that setting maxCompletionTokens to null doesn't trigger validation
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxTokens(50).maxCompletionTokens(null).build();

		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxTokens() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxTokens(100).build();

		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxCompletionTokens() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxCompletionTokens(150).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(150);
	}

	@Test
	void testToolCallbacksAndNames() {
		ToolCallback callback1 = new ToolCallback() {
			@Override
			public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
				return org.springframework.ai.tool.definition.DefaultToolDefinition.builder()
					.name("tool1")
					.description("desc1")
					.inputSchema("{}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return "result1";
			}
		};

		ToolCallback callback2 = new ToolCallback() {
			@Override
			public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
				return org.springframework.ai.tool.definition.DefaultToolDefinition.builder()
					.name("tool2")
					.description("desc2")
					.inputSchema("{}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return "result2";
			}
		};

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.toolCallbacks(callback1, callback2)
			.toolNames("tool1", "tool2")
			.build();

		assertThat(options.getToolCallbacks()).hasSize(2).containsExactly(callback1, callback2);
		assertThat(options.getToolNames()).hasSize(2).contains("tool1", "tool2");
	}

	@Test
	void testToolCallbacksList() {
		ToolCallback callback = new ToolCallback() {
			@Override
			public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
				return org.springframework.ai.tool.definition.DefaultToolDefinition.builder()
					.name("tool")
					.description("desc")
					.inputSchema("{}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return "result";
			}
		};
		List<ToolCallback> callbacks = List.of(callback);

		OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(callbacks).build();

		assertThat(options.getToolCallbacks()).hasSize(1).containsExactly(callback);
	}

	@Test
	void testToolNamesSet() {
		Set<String> toolNames = new HashSet<>(Set.of("tool1", "tool2", "tool3"));

		OpenAiChatOptions options = OpenAiChatOptions.builder().toolNames(toolNames).build();

		assertThat(options.getToolNames()).hasSize(3).containsExactlyInAnyOrder("tool1", "tool2", "tool3");
	}

	@Test
	@SuppressWarnings("DataFlowIssue")
	void testSetToolCallbacksValidation() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().build();

		// Test null validation
		assertThatThrownBy(() -> options.setToolCallbacks(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallbacks cannot be null");

		// Test null elements validation
		List<ToolCallback> callbacksWithNull = new ArrayList<>();
		callbacksWithNull.add(null);
		assertThatThrownBy(() -> options.setToolCallbacks(callbacksWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallbacks cannot contain null elements");
	}

	@Test
	@SuppressWarnings("DataFlowIssue")
	void testSetToolNamesValidation() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().build();

		// Test null validation
		assertThatThrownBy(() -> options.setToolNames(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot be null");

		// Test null elements validation
		Set<String> toolNamesWithNull = new HashSet<>();
		toolNamesWithNull.add(null);
		assertThatThrownBy(() -> options.setToolNames(toolNamesWithNull)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot contain null elements");

		// Test empty string validation
		Set<String> toolNamesWithEmpty = new HashSet<>();
		toolNamesWithEmpty.add("");
		assertThatThrownBy(() -> options.setToolNames(toolNamesWithEmpty)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot contain empty elements");

		// Test whitespace string validation
		Set<String> toolNamesWithWhitespace = new HashSet<>();
		toolNamesWithWhitespace.add("   ");
		assertThatThrownBy(() -> options.setToolNames(toolNamesWithWhitespace))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot contain empty elements");
	}

	@Test
	void testCombineWith() {
		OpenAiChatOptions base = OpenAiChatOptions.builder()
			.model("base-model")
			.temperature(0.5)
			.maxTokens(100)
			.build();

		OpenAiChatOptions override = OpenAiChatOptions.builder().model("override-model").topP(0.9).build();

		OpenAiChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Model should be overridden
		assertThat(merged.getModel()).isEqualTo("override-model");
		// Temperature should be preserved from base
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		// MaxTokens should be preserved from base
		assertThat(merged.getMaxTokens()).isEqualTo(100);
		// TopP should come from override
		assertThat(merged.getTopP()).isEqualTo(0.9);
	}

	@Test
	void testMutateAndBuild() {
		Map<String, Integer> logitBias = Map.of("token", 1);
		List<String> stop = List.of("stop");
		Map<String, String> metadata = Map.of("key", "value");

		OpenAiChatOptions source = OpenAiChatOptions.builder()
			.model("source-model")
			.temperature(0.7)
			.maxTokens(100)
			.logitBias(logitBias)
			.stop(stop)
			.metadata(metadata)
			.build();

		OpenAiChatOptions copy = source.mutate().build();

		assertThat(copy.getModel()).isEqualTo("source-model");
		assertThat(copy.getTemperature()).isEqualTo(0.7);
		assertThat(copy.getMaxTokens()).isEqualTo(100);
		assertThat(copy.getLogitBias()).isEqualTo(logitBias);
		assertThat(copy.getStop()).isEqualTo(stop);
		assertThat(copy.getMetadata()).isEqualTo(metadata);
	}

	@Test
	void testCombineWithDoesNotOverrideWithNull() {
		OpenAiChatOptions base = OpenAiChatOptions.builder()
			.model("base-model")
			.temperature(0.5)
			.maxTokens(100)
			.build();

		OpenAiChatOptions override = OpenAiChatOptions.builder().model(null).temperature(null).build();

		OpenAiChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Null values should not override
		assertThat(merged.getModel()).isEqualTo("base-model");
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		assertThat(merged.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testCombineWithPreservesNonNullValues() {
		OpenAiChatOptions base = OpenAiChatOptions.builder()
			.model("base-model")
			.temperature(0.5)
			.reasoningEffort("medium")
			.build();

		OpenAiChatOptions override = OpenAiChatOptions.builder()
			.model("override-model")
			.reasoningEffort("high")
			.build();

		OpenAiChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getModel()).isEqualTo("override-model");
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		assertThat(merged.getReasoningEffort()).isEqualTo("high");
	}

	@Test
	void testToString() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").temperature(0.7).build();

		String toString = options.toString();
		assertThat(toString).contains("OpenAiChatOptions");
		assertThat(toString).contains("test-model");
		assertThat(toString).contains("0.7");
	}

	@Test
	void testTopKReturnsNull() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().build();
		// TopK is not supported by OpenAI, should always return null
		assertThat(options.getTopK()).isNull();
	}

	@Test
	void testSetOutputSchema() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().build();
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

		options = options.mutate().outputSchema(schema).build();

		assertThat(options.getResponseFormat()).isNotNull();
		assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(options.getResponseFormat().getJsonSchema()).isEqualTo(schema);
		assertThat(options.getOutputSchema()).isEqualTo(schema);
	}

}
