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

package org.springframework.ai.minimax.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionChunk;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ReasoningDetail;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.Role;

import static org.assertj.core.api.Assertions.assertThat;

class MiniMaxStreamFunctionCallingHelperTest {

	private final MiniMaxStreamFunctionCallingHelper helper = new MiniMaxStreamFunctionCallingHelper();

	@Test
	void mergePreservesLatestReasoningDetailsSnapshot() {
		ChatCompletionMessage previousMessage = new ChatCompletionMessage("", Role.ASSISTANT, null, null, null,
				List.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "hello")));
		ChatCompletionMessage currentMessage = new ChatCompletionMessage("done", Role.ASSISTANT, null, null, null,
				List.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "hello world")));

		ChatCompletionChunk previous = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, previousMessage, null)), 1L, "MiniMax-M2.7", null,
				null);
		ChatCompletionChunk current = new ChatCompletionChunk("id",
				List.of(new ChatCompletionChunk.ChunkChoice(null, 0, currentMessage, null)), 1L, "MiniMax-M2.7", null,
				null);

		ChatCompletionChunk merged = this.helper.merge(previous, current);

		assertThat(merged.choices()).hasSize(1);
		assertThat(merged.choices().get(0).delta().reasoningDetails()).containsExactly(
				new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "hello world"));
		assertThat(merged.choices().get(0).delta().content()).isEqualTo("done");
	}

}
