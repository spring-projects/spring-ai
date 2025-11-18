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

package org.springframework.ai.openaiofficial.chat;

import com.openai.models.FunctionDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openaiofficial.OpenAiOfficialChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenAiOfficialChatOptions}.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);
		logitBias.put("token2", -1);

		List<String> stop = List.of("stop1", "stop2");
		List<FunctionDefinition> tools = new ArrayList<>();
		Map<String, String> metadata = Map.of("key1", "value1");
		Map<String, Object> toolContext = Map.of("keyA", "valueA");
		Map<String, String> httpHeaders = Map.of("header1", "value1");

		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
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
			.streamUsage(true)
			.seed(12345)
			.stop(stop)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.user("test-user")
			.parallelToolCalls(true)
			.store(false)
			.metadata(metadata)
			.reasoningEffort("medium")
			.verbosity("low")
			.serviceTier("auto")
			.internalToolExecutionEnabled(false)
			.httpHeaders(httpHeaders)
			.toolContext(toolContext)
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
		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getStop()).isEqualTo(stop);
		assertThat(options.getStopSequences()).isEqualTo(stop);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getTools()).isEqualTo(tools);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getParallelToolCalls()).isTrue();
		assertThat(options.getStore()).isFalse();
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getReasoningEffort()).isEqualTo("medium");
		assertThat(options.getVerbosity()).isEqualTo("low");
		assertThat(options.getServiceTier()).isEqualTo("auto");
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getHttpHeaders()).isEqualTo(httpHeaders);
		assertThat(options.getToolContext()).isEqualTo(toolContext);
	}

	@Test
	void testCopy() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> stop = List.of("stop1");
		List<FunctionDefinition> tools = new ArrayList<>();
		Map<String, String> metadata = Map.of("key1", "value1");

		OpenAiOfficialChatOptions originalOptions = OpenAiOfficialChatOptions.builder()
			.model("test-model")
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(logitBias)
			.logprobs(true)
			.topLogprobs(5)
			.maxCompletionTokens(50)
			.N(2)
			.presencePenalty(0.8)
			.streamUsage(false)
			.seed(12345)
			.stop(stop)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.user("test-user")
			.parallelToolCalls(false)
			.store(true)
			.metadata(metadata)
			.reasoningEffort("low")
			.verbosity("high")
			.serviceTier("default")
			.internalToolExecutionEnabled(true)
			.httpHeaders(Map.of("header1", "value1"))
			.build();

		OpenAiOfficialChatOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
		// Verify collections are copied
		assertThat(copiedOptions.getStop()).isNotSameAs(originalOptions.getStop());
		assertThat(copiedOptions.getHttpHeaders()).isNotSameAs(originalOptions.getHttpHeaders());
		assertThat(copiedOptions.getToolCallbacks()).isNotSameAs(originalOptions.getToolCallbacks());
		assertThat(copiedOptions.getToolNames()).isNotSameAs(originalOptions.getToolNames());
		assertThat(copiedOptions.getToolContext()).isNotSameAs(originalOptions.getToolContext());
	}

	@Test
	void testSetters() {
		Map<String, Integer> logitBias = new HashMap<>();
		logitBias.put("token1", 1);

		List<String> stop = List.of("stop1", "stop2");
		List<FunctionDefinition> tools = new ArrayList<>();
		Map<String, String> metadata = Map.of("key2", "value2");

		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();
		options.setModel("test-model");
		options.setDeploymentName("test-deployment");
		options.setFrequencyPenalty(0.5);
		options.setLogitBias(logitBias);
		options.setLogprobs(true);
		options.setTopLogprobs(5);
		options.setMaxTokens(100);
		options.setMaxCompletionTokens(50);
		options.setN(2);
		options.setPresencePenalty(0.8);
		options.setStreamUsage(true);
		options.setSeed(12345);
		options.setStop(stop);
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setTools(tools);
		options.setUser("test-user");
		options.setParallelToolCalls(true);
		options.setStore(false);
		options.setMetadata(metadata);
		options.setReasoningEffort("high");
		options.setVerbosity("medium");
		options.setServiceTier("auto");
		options.setInternalToolExecutionEnabled(false);
		options.setHttpHeaders(Map.of("header2", "value2"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getDeploymentName()).isEqualTo("test-deployment");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getLogitBias()).isEqualTo(logitBias);
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(5);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getMaxCompletionTokens()).isEqualTo(50);
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getPresencePenalty()).isEqualTo(0.8);
		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getStop()).isEqualTo(stop);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getTools()).isEqualTo(tools);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getParallelToolCalls()).isTrue();
		assertThat(options.getStore()).isFalse();
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getReasoningEffort()).isEqualTo("high");
		assertThat(options.getVerbosity()).isEqualTo("medium");
		assertThat(options.getServiceTier()).isEqualTo("auto");
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getHttpHeaders()).isEqualTo(Map.of("header2", "value2"));
	}

	@Test
	void testDefaultValues() {
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();

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
		assertThat(options.getStreamUsage()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTools()).isNull();
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
		assertThat(options.getHttpHeaders()).isNotNull().isEmpty();
		assertThat(options.getToolContext()).isNotNull().isEmpty();
	}

	@Test
	void testEqualsAndHashCode() {
		OpenAiOfficialChatOptions options1 = OpenAiOfficialChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		OpenAiOfficialChatOptions options2 = OpenAiOfficialChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		OpenAiOfficialChatOptions options3 = OpenAiOfficialChatOptions.builder()
			.model("different-model")
			.temperature(0.7)
			.maxTokens(100)
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
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.temperature(null)
			.logitBias(null)
			.stop(null)
			.tools(null)
			.metadata(null)
			.httpHeaders(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getHttpHeaders()).isNull();
	}

	@Test
	void testBuilderChaining() {
		OpenAiOfficialChatOptions.Builder builder = OpenAiOfficialChatOptions.builder();

		OpenAiOfficialChatOptions.Builder result = builder.model("test-model").temperature(0.7).maxTokens(100);

		assertThat(result).isSameAs(builder);

		OpenAiOfficialChatOptions options = result.build();
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testNullAndEmptyCollections() {
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();

		// Test setting null collections
		options.setLogitBias(null);
		options.setStop(null);
		options.setTools(null);
		options.setMetadata(null);
		options.setHttpHeaders(null);

		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getHttpHeaders()).isNull();

		// Test setting empty collections
		options.setLogitBias(new HashMap<>());
		options.setStop(new ArrayList<>());
		options.setTools(new ArrayList<>());
		options.setMetadata(new HashMap<>());
		options.setHttpHeaders(new HashMap<>());

		assertThat(options.getLogitBias()).isEmpty();
		assertThat(options.getStop()).isEmpty();
		assertThat(options.getTools()).isEmpty();
		assertThat(options.getMetadata()).isEmpty();
		assertThat(options.getHttpHeaders()).isEmpty();
	}

	@Test
	void testStopSequencesAlias() {
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();
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
	void testCopyChangeIndependence() {
		OpenAiOfficialChatOptions original = OpenAiOfficialChatOptions.builder()
			.model("original-model")
			.temperature(0.5)
			.build();

		OpenAiOfficialChatOptions copied = original.copy();

		// Modify original
		original.setModel("modified-model");
		original.setTemperature(0.9);

		// Verify copy is unchanged
		assertThat(copied.getModel()).isEqualTo("original-model");
		assertThat(copied.getTemperature()).isEqualTo(0.5);
	}

	@Test
	void testMaxTokensIsDeprectaed() {
		// Test that setting maxCompletionTokens takes precedence over maxTokens in
		// builder
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.maxCompletionTokens(100)
			.maxTokens(50)
			.build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxCompletionTokensMutualExclusivityValidation() {
		// Test that setting maxCompletionTokens clears maxTokens in builder
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.maxTokens(50)
			.maxCompletionTokens(100)
			.build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxTokensWithNullDoesNotClearMaxCompletionTokens() {
		// Test that setting maxTokens to null doesn't trigger validation
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.maxCompletionTokens(100)
			.maxTokens(null)
			.build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
	}

	@Test
	void testMaxCompletionTokensWithNullDoesNotClearMaxTokens() {
		// Test that setting maxCompletionTokens to null doesn't trigger validation
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.maxTokens(50)
			.maxCompletionTokens(null)
			.build();

		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxTokens() {
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder().maxTokens(100).build();

		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getMaxCompletionTokens()).isNull();
	}

	@Test
	void testBuilderCanSetOnlyMaxCompletionTokens() {
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder().maxCompletionTokens(150).build();

		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(150);
	}

	@Test
	void testSettersMutualExclusivityNotEnforced() {
		// Test that direct setters do NOT enforce mutual exclusivity (only builder does)
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();
		options.setMaxTokens(50);
		options.setMaxCompletionTokens(100);

		// Both should be set when using setters directly
		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
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

		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
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

		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder().toolCallbacks(callbacks).build();

		assertThat(options.getToolCallbacks()).hasSize(1).containsExactly(callback);
	}

	@Test
	void testToolNamesSet() {
		Set<String> toolNames = new HashSet<>(Arrays.asList("tool1", "tool2", "tool3"));

		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder().toolNames(toolNames).build();

		assertThat(options.getToolNames()).hasSize(3).containsExactlyInAnyOrder("tool1", "tool2", "tool3");
	}

	@Test
	@SuppressWarnings("DataFlowIssue")
	void testSetToolCallbacksValidation() {
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();

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
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();

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
	void testBuilderMerge() {
		OpenAiOfficialChatOptions base = OpenAiOfficialChatOptions.builder()
			.model("base-model")
			.temperature(0.5)
			.maxTokens(100)
			.build();

		OpenAiOfficialChatOptions override = OpenAiOfficialChatOptions.builder()
			.model("override-model")
			.topP(0.9)
			.build();

		OpenAiOfficialChatOptions merged = OpenAiOfficialChatOptions.builder().from(base).merge(override).build();

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
	void testBuilderFrom() {
		Map<String, Integer> logitBias = Map.of("token", 1);
		List<String> stop = List.of("stop");
		Map<String, String> metadata = Map.of("key", "value");

		OpenAiOfficialChatOptions source = OpenAiOfficialChatOptions.builder()
			.model("source-model")
			.temperature(0.7)
			.maxTokens(100)
			.logitBias(logitBias)
			.stop(stop)
			.metadata(metadata)
			.build();

		OpenAiOfficialChatOptions copy = OpenAiOfficialChatOptions.builder().from(source).build();

		assertThat(copy.getModel()).isEqualTo("source-model");
		assertThat(copy.getTemperature()).isEqualTo(0.7);
		assertThat(copy.getMaxTokens()).isEqualTo(100);
		assertThat(copy.getLogitBias()).isEqualTo(logitBias);
		assertThat(copy.getStop()).isEqualTo(stop);
		assertThat(copy.getMetadata()).isEqualTo(metadata);
		// Verify collections are copied
		assertThat(copy.getStop()).isNotSameAs(source.getStop());
	}

	@Test
	void testMergeDoesNotOverrideWithNull() {
		OpenAiOfficialChatOptions base = OpenAiOfficialChatOptions.builder()
			.model("base-model")
			.temperature(0.5)
			.maxTokens(100)
			.build();

		OpenAiOfficialChatOptions override = OpenAiOfficialChatOptions.builder().model(null).temperature(null).build();

		OpenAiOfficialChatOptions merged = OpenAiOfficialChatOptions.builder().from(base).merge(override).build();

		// Null values should not override
		assertThat(merged.getModel()).isEqualTo("base-model");
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		assertThat(merged.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testMergeWithEmptyCollections() {
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

		OpenAiOfficialChatOptions base = OpenAiOfficialChatOptions.builder()
			.toolCallbacks(callback)
			.toolNames("tool1")
			.toolContext(Map.of("key", "value"))
			.build();

		OpenAiOfficialChatOptions override = new OpenAiOfficialChatOptions();

		OpenAiOfficialChatOptions merged = OpenAiOfficialChatOptions.builder().from(base).merge(override).build();

		// Empty collections should not override
		assertThat(merged.getToolCallbacks()).hasSize(1);
		assertThat(merged.getToolNames()).hasSize(1);
		assertThat(merged.getToolContext()).hasSize(1);
	}

	@Test
	void testToString() {
		OpenAiOfficialChatOptions options = OpenAiOfficialChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.build();

		String toString = options.toString();
		assertThat(toString).contains("OpenAiOfficialChatOptions");
		assertThat(toString).contains("test-model");
		assertThat(toString).contains("0.7");
	}

	@Test
	void testTopKReturnsNull() {
		OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();
		// TopK is not supported by OpenAI, should always return null
		assertThat(options.getTopK()).isNull();
	}

}
