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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatModelStreamAdvisor}.
 *
 * @author Thomas Vitale
 * @author Minju Kim
 */
class ChatModelStreamAdvisorTests {

	@Test
	void whenChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatModelStreamAdvisor.builder().chatModel(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenContextHasNullValueThenItIsPropagatedToTheResponses() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse("Hel"), chatResponse("lo")));
		ChatModelStreamAdvisor advisor = ChatModelStreamAdvisor.builder().chatModel(chatModel).build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt("Hello"))
			.context("tenantId", null)
			.build();

		List<ChatClientResponse> responses = advisor.adviseStream(request, mock(StreamAdvisorChain.class))
			.collectList()
			.block();

		assertThat(responses).hasSize(2)
			.allSatisfy(response -> assertThat(response.context()).containsEntry("tenantId", null));
	}

	private static ChatResponse chatResponse(String text) {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text)))).build();
	}

}
