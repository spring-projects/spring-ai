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

package org.springframework.ai.zhipuai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ZhiPuAiChatOptions}.
 *
 * @author YunKui Lu
 */
class ZhiPuAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		List<String> stopSequences = List.of("stop1", "stop2");
		List<ZhiPuAiApi.FunctionTool> tools = new ArrayList<>();
		String toolChoice = "auto";
		Map<String, Object> toolContext = Map.of("keyA", "valueA");
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		Set<String> toolNames = Set.of("tool1", "tool2");
		ChatCompletionRequest.ResponseFormat responseFormat = new ChatCompletionRequest.ResponseFormat("json_object");
		ChatCompletionRequest.Thinking thinking = new ChatCompletionRequest.Thinking("enabled");

		ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stop(stopSequences)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.toolChoice(toolChoice)
			.user("test-user")
			.requestId("test-request-id")
			.doSample(true)
			.toolCallbacks(toolCallbacks)
			.toolNames(toolNames)
			.internalToolExecutionEnabled(false)
			.toolContext(toolContext)
			.responseFormat(responseFormat)
			.thinking(thinking)
			.build();

		assertThat(options)
			.extracting("model", "maxTokens", "stop", "temperature", "topP", "tools", "toolChoice", "user", "requestId",
					"doSample", "toolCallbacks", "toolNames", "internalToolExecutionEnabled", "toolContext",
					"responseFormat", "thinking")
			.containsExactly("test-model", 100, stopSequences, 0.7, 0.9, tools, toolChoice, "test-user",
					"test-request-id", true, toolCallbacks, toolNames, false, toolContext, responseFormat, thinking);
	}

	@Test
	void testCopy() {
		List<String> stopSequences = List.of("stop1");
		List<ZhiPuAiApi.FunctionTool> tools = new ArrayList<>();
		String toolChoice = "none";
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		Set<String> toolNames = Set.of("tool1");
		ChatCompletionRequest.ResponseFormat responseFormat = new ChatCompletionRequest.ResponseFormat("json_object");
		ChatCompletionRequest.Thinking thinking = new ChatCompletionRequest.Thinking("disabled");

		ZhiPuAiChatOptions originalOptions = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.maxTokens(50)
			.stop(stopSequences)
			.temperature(0.7)
			.topP(0.9)
			.tools(tools)
			.toolChoice(toolChoice)
			.user("test-user")
			.requestId("test-request-id")
			.doSample(true)
			.toolCallbacks(toolCallbacks)
			.toolNames(toolNames)
			.internalToolExecutionEnabled(true)
			.toolContext(Map.of("key1", "value1"))
			.responseFormat(responseFormat)
			.thinking(thinking)
			.build();

		ZhiPuAiChatOptions copiedOptions = originalOptions.copy();
		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testSetters() {
		List<String> stopSequences = List.of("stop1", "stop2");
		List<ZhiPuAiApi.FunctionTool> tools = new ArrayList<>();
		String toolChoice = "auto";
		Map<String, Object> toolContext = Map.of("key2", "value2");
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		Set<String> toolNames = Set.of("tool1", "tool2");
		ChatCompletionRequest.ResponseFormat responseFormat = new ChatCompletionRequest.ResponseFormat("json_object");
		ChatCompletionRequest.Thinking thinking = new ChatCompletionRequest.Thinking("enabled");

		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();
		options.setModel("test-model");
		options.setMaxTokens(100);
		options.setStop(stopSequences);
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setTools(tools);
		options.setToolChoice(toolChoice);
		options.setUser("test-user");
		options.setRequestId("test-request-id");
		options.setDoSample(true);
		options.setToolCallbacks(toolCallbacks);
		options.setToolNames(toolNames);
		options.setInternalToolExecutionEnabled(false);
		options.setToolContext(toolContext);
		options.setResponseFormat(responseFormat);
		options.setThinking(thinking);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getStop()).isEqualTo(stopSequences);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getTools()).isEqualTo(tools);
		assertThat(options.getToolChoice()).isEqualTo(toolChoice);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getRequestId()).isEqualTo("test-request-id");
		assertThat(options.getDoSample()).isEqualTo(true);
		assertThat(options.getToolCallbacks()).isEqualTo(toolCallbacks);
		assertThat(options.getToolNames()).isEqualTo(toolNames);
		assertThat(options.getInternalToolExecutionEnabled()).isEqualTo(false);
		assertThat(options.getToolContext()).isEqualTo(toolContext);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(options.getThinking()).isEqualTo(thinking);
		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
	}

	@Test
	void testDefaultValues() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getRequestId()).isNull();
		assertThat(options.getDoSample()).isNull();
		assertThat(options.getToolCallbacks()).isNotNull().isEmpty();
		assertThat(options.getToolNames()).isNotNull().isEmpty();
		assertThat(options.getInternalToolExecutionEnabled()).isNull();
		assertThat(options.getToolContext()).isEqualTo(new HashMap<>());
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getThinking()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getTopK()).isNull();
	}

	@Test
	@SuppressWarnings("SelfAssertion")
	void testEqualsAndHashCode() {
		ZhiPuAiChatOptions options1 = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		ZhiPuAiChatOptions options2 = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		ZhiPuAiChatOptions options3 = ZhiPuAiChatOptions.builder()
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
		ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder().temperature(null).stop(null).tools(null).build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
	}

	@Test
	void testBuilderChaining() {
		ZhiPuAiChatOptions.Builder builder = ZhiPuAiChatOptions.builder();

		ZhiPuAiChatOptions.Builder result = builder.model("test-model").temperature(0.7).maxTokens(100);

		assertThat(result).isSameAs(builder);

		ZhiPuAiChatOptions options = result.build();
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testNullAndEmptyCollections() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();

		// Test setting null collections
		options.setStop(null);
		options.setTools(null);

		assertThat(options.getStop()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getToolCallbacks()).isEmpty();
		assertThat(options.getToolNames()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();

		// Test setting empty collections
		options.setStop(new ArrayList<>());
		options.setTools(new ArrayList<>());
		options.setToolCallbacks(new ArrayList<>());
		options.setToolNames(new HashSet<>());
		options.setToolContext(new HashMap<>());

		assertThat(options.getStop()).isEmpty();
		assertThat(options.getTools()).isEmpty();
		assertThat(options.getToolCallbacks()).isEmpty();
		assertThat(options.getToolNames()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
	}

	@Test
	void testStopSequencesAlias() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();
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
	void testFromOptions() {
		ZhiPuAiChatOptions source = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.doSample(true)
			.requestId("test-request-id")
			.build();

		ZhiPuAiChatOptions result = ZhiPuAiChatOptions.fromOptions(source);
		assertThat(result.getModel()).isEqualTo("test-model");
		assertThat(result.getTemperature()).isEqualTo(0.7);
		assertThat(result.getMaxTokens()).isEqualTo(100);
		assertThat(result.getDoSample()).isEqualTo(true);
		assertThat(result.getRequestId()).isEqualTo("test-request-id");
	}

	@Test
	void testCopyChangeIndependence() {
		ZhiPuAiChatOptions original = ZhiPuAiChatOptions.builder().model("original-model").temperature(0.5).build();

		ZhiPuAiChatOptions copied = original.copy();

		// Modify original
		original.setModel("modified-model");
		original.setTemperature(0.9);

		// Verify copy is unchanged
		assertThat(copied.getModel()).isEqualTo("original-model");
		assertThat(copied.getTemperature()).isEqualTo(0.5);
	}

	@Test
	void testResponseFormatAndThinkingSetters() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();

		ChatCompletionRequest.ResponseFormat responseFormat = new ChatCompletionRequest.ResponseFormat("json_object");
		ChatCompletionRequest.Thinking thinking = new ChatCompletionRequest.Thinking("enabled");

		// Test fluent setters
		ZhiPuAiChatOptions result1 = options.setResponseFormat(responseFormat);
		assertThat(result1).isSameAs(options);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);

		ZhiPuAiChatOptions result2 = options.setThinking(thinking);
		assertThat(result2).isSameAs(options);
		assertThat(options.getThinking()).isEqualTo(thinking);
	}

	@Test
	void testToolCallbacksValidation() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();

		// Test setting valid tool callbacks
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		options.setToolCallbacks(toolCallbacks);
		assertThat(options.getToolCallbacks()).isEqualTo(toolCallbacks);
	}

	@Test
	void testToolNamesValidation() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();

		// Test setting valid tool names
		Set<String> toolNames = Set.of("tool1", "tool2");
		options.setToolNames(toolNames);
		assertThat(options.getToolNames()).isEqualTo(toolNames);
	}

	@Test
	void testBuilderWithToolCallbacksAndNames() {
		ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
			.toolCallbacks(List.of())
			.toolNames(Set.of("tool1", "tool2"))
			.build();

		assertThat(options.getToolCallbacks()).isNotNull().isEmpty();
		assertThat(options.getToolNames()).isEqualTo(Set.of("tool1", "tool2"));
	}

	@Test
	void testToString() {
		ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder().model("test-model").temperature(0.7).build();

		String toString = options.toString();
		assertThat(toString).startsWith("ZhiPuAiChatOptions: ");
		assertThat(toString).contains("test-model");
		assertThat(toString).contains("0.7");
	}

}
