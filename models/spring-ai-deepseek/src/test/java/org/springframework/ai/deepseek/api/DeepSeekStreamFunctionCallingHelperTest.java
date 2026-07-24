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

	/**
	 * vLLM sends the tool call id in every chunk (unlike OpenAI which only sends it
	 * once). When id is present in every chunk, the merge logic must use the tool call
	 * index to determine whether to merge or create a new entry.
	 * <p>
	 * Without the index-based fix, each chunk with a non-empty id would be treated as a
	 * new tool call, resulting in duplicate entries.
	 */
	@Test
	void mergeVllmToolCallsWithIdInEveryChunkShouldUseIndex() {
		// Given: vLLM sends id="call_1" in every chunk with index=0
		ChatCompletionFunction funcChunk1 = new ChatCompletionFunction("get_weather", "{\"location\":");
		ToolCall toolCall1 = new ToolCall(0, "call_1", "function", funcChunk1);

		ChatCompletionFunction funcChunk2 = new ChatCompletionFunction("get_weather", "\"Beijing\"}");
		ToolCall toolCall2 = new ToolCall(0, "call_1", "function", funcChunk2);

		ChatCompletionMessage msg1 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall1));
		ChatCompletionMessage msg2 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall2));

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg1, null)), 123L, "model", null, null, null,
				null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg2, null)), 123L, "model", null, null, null,
				null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then: should have 1 merged tool call, not 2 separate ones
		assertThat(result.choices()).hasSize(1);
		assertThat(result.choices().get(0).delta().toolCalls()).hasSize(1);
		ToolCall merged = result.choices().get(0).delta().toolCalls().get(0);
		assertThat(merged.id()).isEqualTo("call_1");
		assertThat(merged.function().name()).isEqualTo("get_weather");
		assertThat(merged.function().arguments()).isEqualTo("{\"location\":\"Beijing\"}");
	}

	/**
	 * When index is available on two tool calls with different indices, they should NOT
	 * be merged — they represent separate tool calls.
	 */
	@Test
	void mergeWithDifferentIndicesShouldNotMerge() {
		// Given: two parallel tool calls with different indices
		ChatCompletionFunction func1 = new ChatCompletionFunction("func_a", "{\"a\":");
		ToolCall toolCall1 = new ToolCall(0, "call_1", "function", func1);

		ChatCompletionFunction func2 = new ChatCompletionFunction("func_b", "{\"b\":");
		ToolCall toolCall2 = new ToolCall(1, "call_2", "function", func2);

		ChatCompletionMessage msg1 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall1));
		ChatCompletionMessage msg2 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall2));

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg1, null)), 123L, "model", null, null, null,
				null);

		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, msg2, null)), 123L, "model", null, null, null,
				null);

		// When
		ChatCompletionChunk result = this.helper.merge(previous, current);

		// Then: should have 2 separate tool calls
		assertThat(result.choices()).hasSize(1);
		assertThat(result.choices().get(0).delta().toolCalls()).hasSize(2);
		assertThat(result.choices().get(0).delta().toolCalls().get(0).function().name()).isEqualTo("func_a");
		assertThat(result.choices().get(0).delta().toolCalls().get(1).function().name()).isEqualTo("func_b");
	}

}
