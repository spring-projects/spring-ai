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
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatModelCallAdvisor}.
 *
 * @author Thomas Vitale
 */
class ChatModelCallAdvisorTests {

	@Test
	void whenChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatModelCallAdvisor.builder().chatModel(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenNotLastInChainThrow() {
		ChatModel chatModel = mock(ChatModel.class);
		ChatClientRequest chatClientRequest = mock(ChatClientRequest.class);
		CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);

		when(callAdvisorChain.hasNextCallAdvisor()).thenReturn(true);

		ChatModelCallAdvisor chatModelCallAdvisor = ChatModelCallAdvisor.builder().chatModel(chatModel).build();

		assertThatThrownBy(() -> chatModelCallAdvisor.adviseCall(chatClientRequest, callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ChatModelCallAdvisor should be the last CallAdvisor in the chain");
	}

}
