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
		assertion.accept(
				new OpenAiApi.ChatCompletionMessage(null, null, null, null, Collections.emptyList(), null, null, null));
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
				null, null, null));
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
		var delta = new OpenAiApi.ChatCompletionMessage(null, null, null, null, List.of(toolCall), null, null, null);
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

}
