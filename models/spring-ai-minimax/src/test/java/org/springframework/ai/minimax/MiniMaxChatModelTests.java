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

package org.springframework.ai.minimax;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletion;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ReasoningDetail;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MiniMaxChatModelTests {

	@Test
	void callMapsReasoningDetailsToAssistantMetadata() {
		MiniMaxApi miniMaxApi = mock(MiniMaxApi.class);
		MiniMaxChatModel chatModel = new MiniMaxChatModel(miniMaxApi,
				MiniMaxChatOptions.builder().model("MiniMax-M2.7").build());

		ChatCompletionMessage message = new ChatCompletionMessage("Done", ChatCompletionMessage.Role.ASSISTANT, null,
				null, null, List.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0,
						"thinking text")));
		ChatCompletion completion = new ChatCompletion("chatcmpl-1",
				List.of(new ChatCompletion.Choice(MiniMaxApi.ChatCompletionFinishReason.STOP, 0, message, null, null)),
				1L, "MiniMax-M2.7", "fingerprint", "chat.completion", new ChatCompletion.BaseResponse(0L, ""), null);

		BDDMockito.given(miniMaxApi.chatCompletionEntity(BDDMockito.any(ChatCompletionRequest.class)))
			.willReturn(ResponseEntity.ok(completion));

		var response = chatModel.call(new Prompt("Test"));

		assertThat(response.getResult().getOutput().getMetadata()).containsEntry("reasoningDetails", List
			.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "thinking text")));
	}

	@Test
	void createRequestRestoresReasoningDetailsFromAssistantMetadata() {
		MiniMaxChatModel chatModel = new MiniMaxChatModel(new MiniMaxApi("TEST"),
				MiniMaxChatOptions.builder().model("MiniMax-M2.7").build());

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("Tool call response")
			.properties(Map.of("reasoningDetails",
					List.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0,
							"thinking text"))))
			.build();

		ChatCompletionRequest request = chatModel.createRequest(
				new Prompt(List.of(assistantMessage), MiniMaxChatOptions.builder().model("MiniMax-M2.7").build()),
				false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.messages().get(0).reasoningDetails()).containsExactly(
				new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "thinking text"));
	}

}
