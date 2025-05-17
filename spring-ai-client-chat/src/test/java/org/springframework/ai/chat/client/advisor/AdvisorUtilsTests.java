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

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisorUtils}.
 *
 * @author ghdcksgml1
 * @author Thomas Vitale
 */
class AdvisorUtilsTests {

	@Nested
	class OnFinishReason {

		@Test
		void whenChatResponseIsNullThenReturnFalse() {
			ChatClientResponse chatClientResponse = mock(ChatClientResponse.class);
			given(chatClientResponse.chatResponse()).willReturn(null);

			boolean result = AdvisorUtils.onFinishReason().test(chatClientResponse);

			assertFalse(result);
		}

		@Test
		void whenChatResponseResultsIsNullThenReturnFalse() {
			ChatClientResponse chatClientResponse = mock(ChatClientResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			given(chatResponse.getResults()).willReturn(null);
			given(chatClientResponse.chatResponse()).willReturn(chatResponse);

			boolean result = AdvisorUtils.onFinishReason().test(chatClientResponse);

			assertFalse(result);
		}

		@Test
		void whenChatIsRunningThenReturnFalse() {
			ChatClientResponse chatClientResponse = mock(ChatClientResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			Generation generation = new Generation(new AssistantMessage("running.."), ChatGenerationMetadata.NULL);

			given(chatResponse.getResults()).willReturn(List.of(generation));
			given(chatClientResponse.chatResponse()).willReturn(chatResponse);

			boolean result = AdvisorUtils.onFinishReason().test(chatClientResponse);

			assertFalse(result);
		}

		@Test
		void whenChatIsStopThenReturnTrue() {
			ChatClientResponse chatClientResponse = mock(ChatClientResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			Generation generation = new Generation(new AssistantMessage("finish."),
					ChatGenerationMetadata.builder().finishReason("STOP").build());

			given(chatResponse.getResults()).willReturn(List.of(generation));
			given(chatClientResponse.chatResponse()).willReturn(chatResponse);

			boolean result = AdvisorUtils.onFinishReason().test(chatClientResponse);

			assertTrue(result);
		}

	}

}
