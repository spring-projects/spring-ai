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

package org.springframework.ai.openai.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Unit tests for {@link OpenAiStreamFunctionCallingHelper}
 *
 * @author Emmanuel Essien-nta
 */
public class OpenAiStreamFunctionCallingHelperTest {

	private final OpenAiStreamFunctionCallingHelper helper = new OpenAiStreamFunctionCallingHelper();

	@Test
	public void merge_whenInputIsValid() {
		var expectedResult = new OpenAiApi.ChatCompletionChunk("id", Collections.emptyList(),
				System.currentTimeMillis(), "model", "default", "fingerPrint", "object", null);
		var previous = new OpenAiApi.ChatCompletionChunk(null, null, expectedResult.created(), expectedResult.model(),
				expectedResult.serviceTier(), null, null, null);
		var current = new OpenAiApi.ChatCompletionChunk(expectedResult.id(), null, null, null, null,
				expectedResult.systemFingerprint(), expectedResult.object(), expectedResult.usage());

		var result = this.helper.merge(previous, current);
		assertThat(result).isEqualTo(expectedResult);
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkIsNull() {
		assertThat(this.helper.isStreamingToolFunctionCall(null)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkChoicesIsEmpty() {
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Collections.emptyList(), null, null, null, null, null,
				null);
		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkFirstChoiceIsNull() {
		var choice = (org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice) null;
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Arrays.asList(choice), null, null, null, null, null, null);
		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkFirstChoiceDeltaIsNull() {
		var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, null,
				null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Arrays.asList(choice, null), null, null, null, null, null,
				null);
		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkFirstChoiceDeltaToolCallsIsNullOrEmpty() {
		var assertion = (Consumer<OpenAiApi.ChatCompletionMessage>) delta -> {
			var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null,
					delta, null);
			var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);
			assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
		};
		// Test for null.
		assertion.accept(new OpenAiApi.ChatCompletionMessage(null, null));
		// Test for empty.
		assertion.accept(new OpenAiApi.ChatCompletionMessage(null, null, null, null, Collections.emptyList(), null,
				null, null, null));
	}

	@Test
	public void isStreamingToolFunctionCall_whenChatCompletionChunkFirstChoiceDeltaToolCallsIsNonEmpty() {
		var assertion = (Consumer<OpenAiApi.ChatCompletionMessage>) delta -> {
			var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null,
					delta, null);
			var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);
			assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isTrue();
		};
		assertion.accept(new OpenAiApi.ChatCompletionMessage(null, null, null, null,
				List.of(Mockito.mock(org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall.class)),
				null, null, null, null));
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkIsNull() {
		assertThat(this.helper.isStreamingToolFunctionCallFinish(null)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkChoicesIsEmpty() {
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Collections.emptyList(), null, null, null, null, null,
				null);
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkFirstChoiceIsNull() {
		var choice = (org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice) null;
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Arrays.asList(choice), null, null, null, null, null, null);
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkFirstChoiceDeltaIsNull() {
		var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, null,
				null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, Arrays.asList(choice, null), null, null, null, null, null,
				null);
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkFirstChoiceIsNotToolCalls() {
		var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null,
				new OpenAiApi.ChatCompletionMessage(null, null), null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_whenChatCompletionChunkFirstChoiceIsToolCalls() {
		var choice = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(
				OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS, null, new OpenAiApi.ChatCompletionMessage(null, null),
				null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);
		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isTrue();
	}

	@Test
	public void chunkToChatCompletion_whenInputIsValid() {
		var choice1 = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(
				OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS, 1, new OpenAiApi.ChatCompletionMessage(null, null),
				null);
		var choice2 = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice(
				OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS, 2, new OpenAiApi.ChatCompletionMessage(null, null),
				null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice1, choice2), null, null, null, null, null,
				null);
		OpenAiApi.ChatCompletion result = this.helper.chunkToChatCompletion(chunk);
		assertThat(result.object()).isEqualTo("chat.completion");
		assertThat(result.choices()).hasSize(2);
	}

	@Test
	public void mergeCombinesChunkFieldsCorrectly() {
		var previous = new OpenAiApi.ChatCompletionChunk(null, null, 123456789L, "gpt-4", "default", null, null, null);
		var current = new OpenAiApi.ChatCompletionChunk("chat-1", Collections.emptyList(), null, null, null, "fp-456",
				"chat.completion.chunk", null);

		var result = this.helper.merge(previous, current);

		assertThat(result.id()).isEqualTo("chat-1");
		assertThat(result.created()).isEqualTo(123456789L);
		assertThat(result.model()).isEqualTo("gpt-4");
		assertThat(result.systemFingerprint()).isEqualTo("fp-456");
	}

	@Test
	public void isStreamingToolFunctionCallReturnsFalseForNullOrEmptyChunks() {
		assertThat(this.helper.isStreamingToolFunctionCall(null)).isFalse();

		var emptyChunk = new OpenAiApi.ChatCompletionChunk(null, Collections.emptyList(), null, null, null, null, null,
				null);
		assertThat(this.helper.isStreamingToolFunctionCall(emptyChunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCall_returnsTrueForValidToolCalls() {
		var toolCall = Mockito.mock(OpenAiApi.ChatCompletionMessage.ToolCall.class);
		var delta = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall), null, null, null,
				null);
		var choice = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, delta, null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);

		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isTrue();
	}

	@Test
	public void isStreamingToolFunctionCallFinishDetectsToolCallsFinishReason() {
		var choice = new OpenAiApi.ChatCompletionChunk.ChunkChoice(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS,
				null, new OpenAiApi.ChatCompletionMessage(null, null), null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);

		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isTrue();
	}

	@Test
	public void merge_whenBothChunksAreNull() {
		var result = this.helper.merge(null, null);
		assertThat(result).isNull();
	}

	@Test
	public void merge_whenPreviousIsNull() {
		var current = new OpenAiApi.ChatCompletionChunk("id", Collections.emptyList(), System.currentTimeMillis(),
				"model", "default", "fingerprint", "object", null);

		var result = this.helper.merge(null, current);
		assertThat(result).isEqualTo(current);
	}

	@Test
	public void merge_whenCurrentIsNull() {
		var previous = new OpenAiApi.ChatCompletionChunk("id", Collections.emptyList(), System.currentTimeMillis(),
				"model", "default", "fingerprint", "object", null);

		var result = this.helper.merge(previous, null);
		assertThat(result).isEqualTo(previous);
	}

	@Test
	public void merge_partialFieldsFromEachChunk() {
		var choices = List.of(Mockito.mock(OpenAiApi.ChatCompletionChunk.ChunkChoice.class));
		var usage = Mockito.mock(OpenAiApi.Usage.class);

		var previous = new OpenAiApi.ChatCompletionChunk(null, choices, 1L, "model1", null, "fp1", null, null);
		var current = new OpenAiApi.ChatCompletionChunk("id2", null, null, null, "tier2", null, "object2", usage);

		var result = this.helper.merge(previous, current);

		assertThat(result.id()).isEqualTo("id2");
		assertThat(result.choices()).isEqualTo(choices);
		assertThat(result.created()).isEqualTo(1L);
		assertThat(result.model()).isEqualTo("model1");
		assertThat(result.serviceTier()).isEqualTo("tier2");
		assertThat(result.systemFingerprint()).isEqualTo("fp1");
		assertThat(result.object()).isEqualTo("object2");
		assertThat(result.usage()).isEqualTo(usage);
	}

	@Test
	public void isStreamingToolFunctionCall_withMultipleChoicesAndOnlyFirstHasToolCalls() {
		var toolCall = Mockito.mock(OpenAiApi.ChatCompletionMessage.ToolCall.class);
		var deltaWithToolCalls = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall), null,
				null, null, null);
		var deltaWithoutToolCalls = new OpenAiApi.ChatCompletionMessage(null, null);

		var choice1 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, deltaWithToolCalls, null);
		var choice2 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, deltaWithoutToolCalls, null);

		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice1, choice2), null, null, null, null, null,
				null);

		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isTrue();
	}

	@Test
	public void isStreamingToolFunctionCall_withMultipleChoicesAndNoneHaveToolCalls() {
		var deltaWithoutToolCalls = new OpenAiApi.ChatCompletionMessage(null, null);

		var choice1 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, deltaWithoutToolCalls, null);
		var choice2 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, deltaWithoutToolCalls, null);

		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice1, choice2), null, null, null, null, null,
				null);

		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	public void isStreamingToolFunctionCallFinish_withMultipleChoicesAndOnlyFirstIsToolCallsFinish() {
		var choice1 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS,
				null, new OpenAiApi.ChatCompletionMessage(null, null), null);
		var choice2 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(OpenAiApi.ChatCompletionFinishReason.STOP, null,
				new OpenAiApi.ChatCompletionMessage(null, null), null);

		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice1, choice2), null, null, null, null, null,
				null);

		assertThat(this.helper.isStreamingToolFunctionCallFinish(chunk)).isTrue();
	}

	@Test
	public void chunkToChatCompletion_whenChunkIsNull() {
		assertThatThrownBy(() -> this.helper.chunkToChatCompletion(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	public void chunkToChatCompletion_withEmptyChoices() {
		var chunk = new OpenAiApi.ChatCompletionChunk("id", Collections.emptyList(), 1L, "model", "tier", "fp",
				"object", null);

		var result = this.helper.chunkToChatCompletion(chunk);

		assertThat(result.object()).isEqualTo("chat.completion");
		assertThat(result.choices()).isEmpty();
		assertThat(result.id()).isEqualTo("id");
		assertThat(result.created()).isEqualTo(1L);
		assertThat(result.model()).isEqualTo("model");
	}

	@Test
	public void edgeCases_emptyStringFields() {
		var chunk = new OpenAiApi.ChatCompletionChunk("", Collections.emptyList(), 0L, "", "", "", "", null);

		var result = this.helper.chunkToChatCompletion(chunk);

		assertThat(result.id()).isEmpty();
		assertThat(result.model()).isEmpty();
		assertThat(result.serviceTier()).isEmpty();
		assertThat(result.systemFingerprint()).isEmpty();
		assertThat(result.created()).isEqualTo(0L);
	}

	@Test
	public void isStreamingToolFunctionCall_withNullToolCallsList() {
		var delta = new OpenAiApi.ChatCompletionMessage(null, null, null, null, null, null, null, null, null);
		var choice = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, null, delta, null);
		var chunk = new OpenAiApi.ChatCompletionChunk(null, List.of(choice), null, null, null, null, null, null);

		assertThat(this.helper.isStreamingToolFunctionCall(chunk)).isFalse();
	}

	@Test
	public void merge_sameIdToolCallChunks_shouldMergeProperly() {
		// Tests merge() behavior when chunks with same ID arrive separately.
		// This happens when finish_reason is not "tool_calls", causing
		// isStreamingToolFunctionCallFinish() to return false and windowUntil()
		// to fail grouping. Without same-ID merging, this creates multiple
		// tool calls with empty names, causing IllegalArgumentException.

		// Chunk 1: ID + function name
		var toolCall1 = new OpenAiApi.ChatCompletionMessage.ToolCall("call_123", "function",
				new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("get_weather", ""));
		var delta1 = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall1), null, null, null);
		var choice1 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, 0, delta1, null);
		var chunk1 = new OpenAiApi.ChatCompletionChunk("chat-1", List.of(choice1), 1L, "gpt-4", null, null, null, null);

		// Chunk 2: Same ID, empty name, partial arguments
		var toolCall2 = new OpenAiApi.ChatCompletionMessage.ToolCall("call_123", "function",
				new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("", "{\"city\""));
		var delta2 = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall2), null, null, null);
		var choice2 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, 0, delta2, null);
		var chunk2 = new OpenAiApi.ChatCompletionChunk("chat-1", List.of(choice2), null, null, null, null, null, null);

		// Chunk 3: Same ID, empty name, remaining arguments
		var toolCall3 = new OpenAiApi.ChatCompletionMessage.ToolCall("call_123", "function",
				new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("", ":\"Seoul\"}"));
		var delta3 = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall3), null, null, null);
		var choice3 = new OpenAiApi.ChatCompletionChunk.ChunkChoice(null, 0, delta3, null);
		var chunk3 = new OpenAiApi.ChatCompletionChunk("chat-1", List.of(choice3), null, null, null, null, null, null);

		// Merge all chunks
		var merged1 = this.helper.merge(chunk1, chunk2);
		var merged2 = this.helper.merge(merged1, chunk3);

		// Verify: should have exactly one tool call with complete data
		assertThat(merged2.choices()).hasSize(1);
		var finalChoice = merged2.choices().get(0);
		assertThat(finalChoice.delta().toolCalls()).hasSize(1);

		var finalToolCall = finalChoice.delta().toolCalls().get(0);
		assertThat(finalToolCall.id()).isEqualTo("call_123");
		assertThat(finalToolCall.function().name()).isEqualTo("get_weather");
		assertThat(finalToolCall.function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
	}

}
