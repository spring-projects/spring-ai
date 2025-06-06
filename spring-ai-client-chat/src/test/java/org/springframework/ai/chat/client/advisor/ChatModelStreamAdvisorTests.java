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

package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatModelStreamAdvisor}.
 *
 * @author Thomas Vitale
 */
class ChatModelStreamAdvisorTests {

	@Test
	void whenChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatModelStreamAdvisor.builder().chatModel(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenNotLastInChainThrow() {
		ChatModel chatModel = mock(ChatModel.class);
		ChatClientRequest chatClientRequest = mock(ChatClientRequest.class);
		StreamAdvisorChain streamAdvisorChain = mock(StreamAdvisorChain.class);

		when(streamAdvisorChain.hasNextStreamAdvisor()).thenReturn(true);

		ChatModelStreamAdvisor chatModelStreamAdvisor = ChatModelStreamAdvisor.builder().chatModel(chatModel).build();

		assertThatThrownBy(() -> chatModelStreamAdvisor.adviseStream(chatClientRequest, streamAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ChatModelStreamAdvisor should be the last StreamAdvisor in the chain");
	}

}
