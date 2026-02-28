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

	@Test
	void merge_sameIdToolCallChunks_shouldMergeProperly() {
		// Tests merge() behavior when chunks with same ID arrive separately.
		// This happens when finish_reason is not "tool_calls", causing
		// isStreamingToolFunctionCallFinish() to return false and windowUntil()
		// to fail grouping. Without same-ID merging, this creates multiple
		// tool calls with empty names, causing IllegalArgumentException.

		// Chunk 1: ID + function name
		ToolCall toolCall1 = new ToolCall("call_123", "function",
				new ChatCompletionFunction("get_weather", ""));
		ChatCompletionMessage msg1 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall1));
		ChatCompletionChunk chunk1 = new ChatCompletionChunk("chat-1",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg1, null)), 123L, "deepseek-chat", null, null,
				null, null);

		// Chunk 2: Same ID, empty name, partial arguments
		ToolCall toolCall2 = new ToolCall("call_123", "function",
				new ChatCompletionFunction("", "{\"city\""));
		ChatCompletionMessage msg2 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall2));
		ChatCompletionChunk chunk2 = new ChatCompletionChunk("chat-1",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg2, null)), null, null, null, null, null, null);

		// Chunk 3: Same ID, empty name, remaining arguments
		ToolCall toolCall3 = new ToolCall("call_123", "function",
				new ChatCompletionFunction("", ":\"Seoul\"}"));
		ChatCompletionMessage msg3 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall3));
		ChatCompletionChunk chunk3 = new ChatCompletionChunk("chat-1",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg3, null)), null, null, null, null, null, null);

		// Merge all chunks
		ChatCompletionChunk merged1 = this.helper.merge(chunk1, chunk2);
		ChatCompletionChunk merged2 = this.helper.merge(merged1, chunk3);

		// Verify: should have exactly one tool call with complete data
		assertThat(merged2.choices()).hasSize(1);
		var finalChoice = merged2.choices().get(0);
		assertThat(finalChoice.delta().toolCalls()).hasSize(1);

		ToolCall finalToolCall = finalChoice.delta().toolCalls().get(0);
		assertThat(finalToolCall.id()).isEqualTo("call_123");
		assertThat(finalToolCall.function().name()).isEqualTo("get_weather");
		assertThat(finalToolCall.function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
	}

}
