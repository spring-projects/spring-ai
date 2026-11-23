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

package org.springframework.ai.openai;

import java.util.List;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall.Function;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link OpenAiChatModel.ChunkMerger}, covering the merging of streamed
 * tool call deltas by their {@code index} field. OpenAI omits {@code id} and
 * {@code function.name} on continuation deltas, while some OpenAI-compatible providers
 * (e.g. DeepSeek) send them as empty strings instead.
 *
 * @author Jewoo Shin
 */
class OpenAiChatModelChunkMergerTests {

	@Test
	void mergeToolCallDeltasWithAbsentContinuationIds() {
		ChatCompletionChunk merged = OpenAiChatModel.ChunkMerger
			.mergeChunks(List.of(chunk(null, toolCallDelta(0, "call_1", "get_weather", "")),
					chunk(null, toolCallDelta(0, null, null, "{\"city\": ")),
					chunk(null, toolCallDelta(0, null, null, "\"Seoul\"}")), finishChunk()));

		List<ToolCall> toolCalls = merged.choices().get(0).delta().toolCalls().orElseThrow();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).id()).contains("call_1");
		assertThat(toolCalls.get(0).function().orElseThrow().name()).contains("get_weather");
		assertThat(toolCalls.get(0).function().orElseThrow().arguments()).contains("{\"city\": \"Seoul\"}");
	}

	@Test // gh-6374
	void mergeToolCallDeltasWithEmptyContinuationIds() {
		ChatCompletionChunk merged = OpenAiChatModel.ChunkMerger
			.mergeChunks(List.of(chunk(null, toolCallDelta(0, "call_1d70a7ee", "show_options", "")),
					chunk(null, toolCallDelta(0, "", "", "{\"options\": [\"a\"")),
					chunk(null, toolCallDelta(0, "", "", ", \"b\"]}")), finishChunk()));

		assertThat(merged.choices().get(0).finishReason()).contains(FinishReason.TOOL_CALLS);
		List<ToolCall> toolCalls = merged.choices().get(0).delta().toolCalls().orElseThrow();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).id()).contains("call_1d70a7ee");
		assertThat(toolCalls.get(0).function().orElseThrow().name()).contains("show_options");
		assertThat(toolCalls.get(0).function().orElseThrow().arguments()).contains("{\"options\": [\"a\", \"b\"]}");

		ChatCompletion completion = OpenAiChatModel.ChunkMerger.chunkToChatCompletion(merged);
		ChatCompletionMessageFunctionToolCall functionToolCall = completion.choices()
			.get(0)
			.message()
			.toolCalls()
			.orElseThrow()
			.get(0)
			.function()
			.orElseThrow();
		assertThat(functionToolCall.id()).isEqualTo("call_1d70a7ee");
		assertThat(functionToolCall.function().name()).isEqualTo("show_options");
		assertThat(functionToolCall.function().arguments()).isEqualTo("{\"options\": [\"a\", \"b\"]}");
	}

	@Test
	void mergeParallelToolCallDeltasByIndex() {
		ChatCompletionChunk merged = OpenAiChatModel.ChunkMerger
			.mergeChunks(List.of(chunk(null, toolCallDelta(0, "call_a", "tool_a", "")),
					chunk(null, toolCallDelta(1, "call_b", "tool_b", "")),
					chunk(null, toolCallDelta(0, null, null, "{\"a\": 1}")),
					chunk(null, toolCallDelta(1, null, null, "{\"b\": 2}")), finishChunk()));

		List<ToolCall> toolCalls = merged.choices().get(0).delta().toolCalls().orElseThrow();
		assertThat(toolCalls).hasSize(2);
		assertThat(toolCalls.get(0).id()).contains("call_a");
		assertThat(toolCalls.get(0).function().orElseThrow().arguments()).contains("{\"a\": 1}");
		assertThat(toolCalls.get(1).id()).contains("call_b");
		assertThat(toolCalls.get(1).function().orElseThrow().arguments()).contains("{\"b\": 2}");
	}

	@Test
	void mergeMultipleToolCallDeltasInSingleChunk() {
		ChatCompletionChunk merged = OpenAiChatModel.ChunkMerger.mergeChunks(List.of(
				chunk(null, toolCallDelta(0, "call_a", "tool_a", ""), toolCallDelta(1, "call_b", "tool_b", "")),
				chunk(null, toolCallDelta(0, null, null, "{\"a\": 1}"), toolCallDelta(1, null, null, "{\"b\": 2}")),
				finishChunk()));

		List<ToolCall> toolCalls = merged.choices().get(0).delta().toolCalls().orElseThrow();
		assertThat(toolCalls).hasSize(2);
		assertThat(toolCalls.get(0).function().orElseThrow().name()).contains("tool_a");
		assertThat(toolCalls.get(0).function().orElseThrow().arguments()).contains("{\"a\": 1}");
		assertThat(toolCalls.get(1).function().orElseThrow().name()).contains("tool_b");
		assertThat(toolCalls.get(1).function().orElseThrow().arguments()).contains("{\"b\": 2}");
	}

	@Test
	void chunkToChatCompletionRequiresToolCallId() {
		ChatCompletionChunk chunk = chunk(FinishReason.TOOL_CALLS,
				toolCallDelta(0, null, "get_weather", "{\"city\":\"Seoul\"}"));
		ChatCompletionChunk blankIdChunk = chunk(FinishReason.TOOL_CALLS,
				toolCallDelta(0, " ", "get_weather", "{\"city\":\"Seoul\"}"));

		assertThatIllegalStateException().isThrownBy(() -> OpenAiChatModel.ChunkMerger.chunkToChatCompletion(chunk))
			.withMessage("Tool call id is missing");
		assertThatIllegalStateException()
			.isThrownBy(() -> OpenAiChatModel.ChunkMerger.chunkToChatCompletion(blankIdChunk))
			.withMessage("Tool call id is missing");
	}

	@Test
	void chunkToChatCompletionRequiresToolCallFunction() {
		ChatCompletionChunk chunk = chunk(FinishReason.TOOL_CALLS, toolCallDeltaWithoutFunction(0, "call_1"));

		assertThatIllegalStateException().isThrownBy(() -> OpenAiChatModel.ChunkMerger.chunkToChatCompletion(chunk))
			.withMessage("Tool call function is missing");
	}

	@Test
	void chunkToChatCompletionRequiresToolCallFunctionName() {
		ChatCompletionChunk chunk = chunk(FinishReason.TOOL_CALLS,
				toolCallDelta(0, "call_1", null, "{\"city\":\"Seoul\"}"));
		ChatCompletionChunk blankNameChunk = chunk(FinishReason.TOOL_CALLS,
				toolCallDelta(0, "call_1", " ", "{\"city\":\"Seoul\"}"));

		assertThatIllegalStateException().isThrownBy(() -> OpenAiChatModel.ChunkMerger.chunkToChatCompletion(chunk))
			.withMessage("Tool call function name is missing");
		assertThatIllegalStateException()
			.isThrownBy(() -> OpenAiChatModel.ChunkMerger.chunkToChatCompletion(blankNameChunk))
			.withMessage("Tool call function name is missing");
	}

	@Test
	void chunkToChatCompletionUsesEmptyDefaultForMissingArguments() {
		ChatCompletionChunk chunk = chunk(FinishReason.TOOL_CALLS,
				toolCallDeltaWithoutArguments(0, "call_1", "get_weather"));

		ChatCompletion completion = OpenAiChatModel.ChunkMerger.chunkToChatCompletion(chunk);

		ChatCompletionMessageFunctionToolCall functionToolCall = completion.choices()
			.get(0)
			.message()
			.toolCalls()
			.orElseThrow()
			.get(0)
			.function()
			.orElseThrow();
		assertThat(functionToolCall.id()).isEqualTo("call_1");
		assertThat(functionToolCall.function().name()).isEqualTo("get_weather");
		assertThat(functionToolCall.function().arguments()).isEmpty();
	}

	private static ChatCompletionChunk chunk(@Nullable FinishReason finishReason, ToolCall... toolCalls) {
		Delta.Builder delta = Delta.builder();
		if (toolCalls.length > 0) {
			delta.toolCalls(List.of(toolCalls));
		}
		return ChatCompletionChunk.builder()
			.id("chatcmpl-test")
			.choices(List.of(Choice.builder().index(0L).delta(delta.build()).finishReason(finishReason).build()))
			.created(1L)
			.model("test-model")
			.build();
	}

	private static ChatCompletionChunk finishChunk() {
		return chunk(FinishReason.TOOL_CALLS);
	}

	private static ToolCall toolCallDelta(long index, @Nullable String id, @Nullable String name, String arguments) {
		Function.Builder function = Function.builder().arguments(arguments);
		if (name != null) {
			function.name(name);
		}
		ToolCall.Builder toolCall = ToolCall.builder().index(index).function(function.build());
		if (id != null) {
			toolCall.id(id);
		}
		return toolCall.build();
	}

	private static ToolCall toolCallDeltaWithoutArguments(long index, String id, String name) {
		return ToolCall.builder().index(index).id(id).function(Function.builder().name(name).build()).build();
	}

	private static ToolCall toolCallDeltaWithoutFunction(long index, String id) {
		return ToolCall.builder().index(index).id(id).build();
	}

}
