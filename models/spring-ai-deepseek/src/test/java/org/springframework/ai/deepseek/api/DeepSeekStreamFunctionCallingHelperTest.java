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

package org.springframework.ai.deepseek.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link DeepSeekStreamFunctionCallingHelper}.
 *
 * @author Sun Yuhan
 */
class DeepSeekStreamFunctionCallingHelperTest {

	private DeepSeekStreamFunctionCallingHelper helper;

	@BeforeEach
	void setUp() {
		this.helper = new DeepSeekStreamFunctionCallingHelper();
	}

	@Test
	void mergeWhenPreviousIsNullShouldReturnCurrent() {
		// Given
		ChatCompletionChunk current = new ChatCompletionChunk("id1", List.of(), 123L, "model1", null, null, null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(null, current);

		// Then
		assertThat(result).isEqualTo(current);
	}

	@Test
	void mergeShouldMergeBasicFieldsFromCurrentAndPrevious() {
		// Given
		ChatCompletionChunk previous = new ChatCompletionChunk("id1", List.of(), 123L, "model1", null, null, null,
				null);
		ChatCompletionChunk current = new ChatCompletionChunk("id2", List.of(), null, null, null, null, null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.id()).isEqualTo("id2"); // from current
		assertThat(result.created()).isEqualTo(123L); // from previous
		assertThat(result.model()).isEqualTo("model1"); // from previous
	}

	@Test
	void mergeShouldMergeMessagesContent() {
		// Given
		ChatCompletionMessage previousMsg = new ChatCompletionMessage("Hello ", Role.ASSISTANT, null, null, null);
		ChatCompletionMessage currentMsg = new ChatCompletionMessage("World!", Role.ASSISTANT, null, null, null);

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices().get(0).delta().content()).isEqualTo("Hello World!");
	}

	@Test
	void mergeShouldHandleToolCallsMerging() {
		// Given
		ChatCompletionFunction func1 = new ChatCompletionFunction("func1", "{\"arg1\":");
		ToolCall toolCall1 = new ToolCall("call_123", "function", func1);
		ChatCompletionMessage previousMsg = new ChatCompletionMessage("content", Role.ASSISTANT, null, null,
				List.of(toolCall1));

		ChatCompletionFunction func2 = new ChatCompletionFunction("func1", "\"value1\"}");
		ToolCall toolCall2 = new ToolCall(null, "function", func2); // No ID -
																	// continuation
		ChatCompletionMessage currentMsg = new ChatCompletionMessage("content", Role.ASSISTANT, null, null,
				List.of(toolCall2));

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices()).hasSize(1);
		assertThat(result.choices().get(0).delta().toolCalls()).hasSize(1);
		ToolCall mergedToolCall = result.choices().get(0).delta().toolCalls().get(0);
		assertThat(mergedToolCall.id()).isEqualTo("call_123");
		assertThat(mergedToolCall.function().name()).isEqualTo("func1");
		assertThat(mergedToolCall.function().arguments()).isEqualTo("{\"arg1\":\"value1\"}");
	}

	@Test
	void mergeWithSingleToolCallShouldWork() {
		// Given
		ToolCall toolCall = new ToolCall("call_1", "function", new ChatCompletionFunction("func1", "{}"));
		ChatCompletionMessage msg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall));

		ChatCompletionChunk previous = new ChatCompletionChunk("id", List.of(), 123L, "model", null, null, null, null);
		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg, null)), 123L, "model", null, null, null,
				null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.choices().get(0).delta().toolCalls()).hasSize(1);
	}

	@Test
	void isStreamingToolFunctionCallWhenNullChunkShouldReturnFalse() {
		// When & Then
		assertThat(this.helper.isStreamingToolFunctionCall(null)).isFalse();
	}

	@Test
	void isStreamingToolFunctionCallWhenEmptyChoicesShouldReturnFalse() {
		// Given
		ChatCompletionChunk chunk = new ChatCompletionChunk("id", List.of(), 123L, "model", null, null, null, null);

		// When & Then
		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	void isStreamingToolFunctionCallWhenHasToolCallsShouldReturnTrue() {
		// Given
		ToolCall toolCall = new ToolCall("call_1", "function", new ChatCompletionFunction("func", "{}"));
		ChatCompletionMessage msg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall));
		ChatCompletionChunk chunk = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg, null)), 123L, "model", null, null, null,
				null);

		// When & Then
		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isTrue();
	}

	@Test
	void isStreamingToolFunctionCallFinishWhenFinishReasonIsToolCallsShouldReturnTrue() {
		// Given
		ChatCompletionMessage msg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null);
		ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice(
				DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS, 0, msg, null);
		ChatCompletionChunk chunk = new ChatCompletionChunk("id", List.of(choice), 123L, "model", null, null, null,
				null);

		// When & Then
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isTrue();
	}

	@Test
	void mergeShouldHandleNullCurrentContent() {
		// Given
		ChatCompletionMessage previousMsg = new ChatCompletionMessage("Hello", Role.ASSISTANT, null, null, null);
		ChatCompletionMessage currentMsg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null);

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices().get(0).delta().content()).isEqualTo("Hello");
	}

	@Test
	void mergeShouldAccumulateReasoningContentAcrossChunks() {
		// Given: streamed deltas where reasoning_content is split across chunks and a
		// tool call arrives only at the end (the failure mode reported in #5898).
		ChatCompletionMessage previousMsg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null,
				"Let me think ");
		ChatCompletionMessage currentMsg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null,
				"about this.");

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices().get(0).delta().reasoningContent()).isEqualTo("Let me think about this.");
	}

	@Test
	void mergeShouldHandleNullCurrentReasoningContent() {
		// Given
		ChatCompletionMessage previousMsg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null,
				"partial reasoning");
		ChatCompletionMessage currentMsg = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null,
				null);

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices().get(0).delta().reasoningContent()).isEqualTo("partial reasoning");
	}

	@Test
	void mergeShouldPreservePrefixFromPreviousWhenCurrentIsNull() {
		// Given
		ChatCompletionMessage previousMsg = new ChatCompletionMessage("hello", Role.ASSISTANT, null, null, null, true,
				null);
		ChatCompletionMessage currentMsg = new ChatCompletionMessage(" world", Role.ASSISTANT, null, null, null, null,
				null);

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result.choices().get(0).delta().prefix()).isTrue();
	}

	@Test
	void expandToolCallWindowEmitsPerChunkDeltasFollowedByMergedFrame() {
		// Given: an SSE-shaped tool-call window where only the first chunk carries id +
		// name, subsequent chunks carry only argument fragments, and the final chunk is
		// an empty delta with finish_reason = TOOL_CALLS.
		ChatCompletionChunk first = chunkWithToolCall(
				new ToolCall(0, "call_abc", "function", new ChatCompletionFunction("write_code", "")), null);
		ChatCompletionChunk second = chunkWithToolCall(
				new ToolCall(0, null, null, new ChatCompletionFunction(null, "def ")), null);
		ChatCompletionChunk third = chunkWithToolCall(
				new ToolCall(0, null, null, new ChatCompletionFunction(null, "hello")), null);
		ChatCompletionChunk finishMarker = chunkWithToolCall(null, DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS);

		// When
		List<ChatCompletionChunk> expanded = this.helper
			.expandToolCallWindow(List.of(first, second, third, finishMarker));

		// Then: three partial frames are emitted (the empty finish marker is dropped) +
		// one terminal merged frame with concatenated arguments and finish_reason.
		assertThat(expanded).hasSize(4);

		// Each partial frame keeps its own argument fragment and stamps the cumulative
		// id/name; finish_reason stays unset so downstream can tell partials apart from
		// the merged frame.
		ToolCall partial1 = expanded.get(0).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial1.id()).isEqualTo("call_abc");
		assertThat(partial1.function().name()).isEqualTo("write_code");
		assertThat(partial1.function().arguments()).isEmpty();
		assertThat(expanded.get(0).choices().get(0).finishReason()).isNull();

		ToolCall partial2 = expanded.get(1).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial2.id()).isEqualTo("call_abc"); // stamped from cumulative
		assertThat(partial2.function().name()).isEqualTo("write_code"); // stamped from
																		// cumulative
		assertThat(partial2.function().arguments()).isEqualTo("def ");
		assertThat(expanded.get(1).choices().get(0).finishReason()).isNull();

		ToolCall partial3 = expanded.get(2).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial3.id()).isEqualTo("call_abc");
		assertThat(partial3.function().name()).isEqualTo("write_code");
		assertThat(partial3.function().arguments()).isEqualTo("hello");
		assertThat(expanded.get(2).choices().get(0).finishReason()).isNull();

		// Final merged frame: full concatenated arguments + finish_reason carried over
		// from the input's empty marker chunk.
		ToolCall merged = expanded.get(3).choices().get(0).delta().toolCalls().get(0);
		assertThat(merged.id()).isEqualTo("call_abc");
		assertThat(merged.function().name()).isEqualTo("write_code");
		assertThat(merged.function().arguments()).isEqualTo("def hello");
		assertThat(expanded.get(3).choices().get(0).finishReason())
			.isEqualTo(DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS);
	}

	@Test
	void expandToolCallWindowPassesSingleChunkThrough() {
		ChatCompletionChunk only = chunkWithToolCall(
				new ToolCall(0, "call_xyz", "function", new ChatCompletionFunction("noop", "{}")),
				DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS);

		List<ChatCompletionChunk> expanded = this.helper.expandToolCallWindow(List.of(only));

		// Single-chunk windows are passed through unchanged so reasoning / text deltas
		// retain their existing streaming UX.
		assertThat(expanded).hasSize(1).containsExactly(only);
	}

	@Test
	void expandToolCallWindowReturnsEmptyListForEmptyInput() {
		assertThat(this.helper.expandToolCallWindow(List.of())).isEmpty();
	}

	private static ChatCompletionChunk chunkWithToolCall(ToolCall toolCall,
			DeepSeekApi.ChatCompletionFinishReason finishReason) {
		List<ToolCall> toolCalls = (toolCall == null) ? List.of() : List.of(toolCall);
		ChatCompletionMessage delta = new ChatCompletionMessage(null, ChatCompletionMessage.Role.ASSISTANT, null, null,
				toolCalls);
		ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice(finishReason, 0, delta, null);
		return new ChatCompletionChunk("id", List.of(choice), 123L, "model", null, null, null, null);
	}

	@Test
	void mergeWhenCurrentToolCallsIsEmptyListShouldNotThrowException() {
		// Given
		ToolCall toolCall = new ToolCall("call_1", "function", new ChatCompletionFunction("func1", "{}"));
		ChatCompletionMessage previousMsg = new ChatCompletionMessage("content", Role.ASSISTANT, null, null,
				List.of(toolCall));

		// Empty list instead of null
		ChatCompletionMessage currentMsg = new ChatCompletionMessage("content", Role.ASSISTANT, null, null, List.of());

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMsg, null)), 123L, "model", null, null,
				null, null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMsg, null)), 123L, "model", null, null,
				null, null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then
		assertThat(result).isNotNull();
	}

}
