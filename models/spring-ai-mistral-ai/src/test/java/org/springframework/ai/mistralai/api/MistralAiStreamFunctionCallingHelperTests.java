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

package org.springframework.ai.mistralai.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionFinishReason;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MistralAiStreamFunctionCallingHelper#expandToolCallWindow}.
 *
 * @author Spring AI
 */
class MistralAiStreamFunctionCallingHelperTests {

	private MistralAiStreamFunctionCallingHelper helper;

	@BeforeEach
	void setUp() {
		this.helper = new MistralAiStreamFunctionCallingHelper();
	}

	@Test
	void expandToolCallWindowEmitsPerChunkDeltasFollowedByMergedFrame() {
		ChatCompletionChunk first = chunk(
				new ToolCall("call_abc", "function", new ChatCompletionFunction("write_code", ""), 0), null);
		ChatCompletionChunk second = chunk(new ToolCall(null, null, new ChatCompletionFunction(null, "def "), 0), null);
		ChatCompletionChunk third = chunk(new ToolCall(null, null, new ChatCompletionFunction(null, "hello"), 0), null);
		ChatCompletionChunk finishMarker = chunk(null, ChatCompletionFinishReason.TOOL_CALLS);

		List<ChatCompletionChunk> expanded = this.helper
			.expandToolCallWindow(List.of(first, second, third, finishMarker));

		assertThat(expanded).hasSize(4);

		// Pre-merge frames keep their own argument fragment, stamp the cumulative
		// id/name/index, and leave finish_reason unset.
		ToolCall partial1 = expanded.get(0).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial1.id()).isEqualTo("call_abc");
		assertThat(partial1.function().name()).isEqualTo("write_code");
		assertThat(partial1.function().arguments()).isEmpty();
		assertThat(expanded.get(0).choices().get(0).finishReason()).isNull();

		ToolCall partial2 = expanded.get(1).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial2.id()).isEqualTo("call_abc");
		assertThat(partial2.function().name()).isEqualTo("write_code");
		assertThat(partial2.function().arguments()).isEqualTo("def ");
		assertThat(expanded.get(1).choices().get(0).finishReason()).isNull();

		ToolCall partial3 = expanded.get(2).choices().get(0).delta().toolCalls().get(0);
		assertThat(partial3.function().arguments()).isEqualTo("hello");
		assertThat(expanded.get(2).choices().get(0).finishReason()).isNull();

		// Final merged frame: full id/name + concatenated arguments + finish_reason.
		ToolCall merged = expanded.get(3).choices().get(0).delta().toolCalls().get(0);
		assertThat(merged.id()).isEqualTo("call_abc");
		assertThat(merged.function().name()).isEqualTo("write_code");
		assertThat(merged.function().arguments()).isEqualTo("def hello");
		assertThat(expanded.get(3).choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.TOOL_CALLS);
	}

	@Test
	void expandToolCallWindowPassesSingleChunkThrough() {
		ChatCompletionChunk only = chunk(
				new ToolCall("call_xyz", "function", new ChatCompletionFunction("noop", "{}"), 0),
				ChatCompletionFinishReason.TOOL_CALLS);
		List<ChatCompletionChunk> expanded = this.helper.expandToolCallWindow(List.of(only));
		assertThat(expanded).hasSize(1).containsExactly(only);
	}

	@Test
	void expandToolCallWindowReturnsEmptyForEmptyInput() {
		assertThat(this.helper.expandToolCallWindow(List.of())).isEmpty();
	}

	private static ChatCompletionChunk chunk(ToolCall toolCall, ChatCompletionFinishReason finishReason) {
		List<ToolCall> toolCalls = (toolCall == null) ? null : List.of(toolCall);
		ChatCompletionMessage delta = new ChatCompletionMessage(null, Role.ASSISTANT, null, toolCalls);
		ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice(0, delta, finishReason, null);
		return new ChatCompletionChunk("id", "obj", 123L, "model", List.of(choice), null);
	}

}
