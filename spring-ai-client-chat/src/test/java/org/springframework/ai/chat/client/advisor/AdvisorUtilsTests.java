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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisorUtils}.
 *
 * @author ghdcksgml1
 * @author Thomas Vitale
 * @author Christian Tzolov
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

	@Nested
	class CopyChainAfterAdvisor {

		@Test
		void whenCallAdvisorChainIsNullThenThrowException() {
			CallAdvisor advisor = mock(CallAdvisor.class);

			assertThatThrownBy(() -> AdvisorUtils.copyChainAfterAdvisor(null, advisor))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("callAdvisorChain must not be null");
		}

		@Test
		void whenAfterAdvisorIsNullThenThrowException() {
			CallAdvisorChain chain = mock(CallAdvisorChain.class);

			assertThatThrownBy(() -> AdvisorUtils.copyChainAfterAdvisor(chain, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The after call advisor must not be null");
		}

		@Test
		void whenAdvisorNotInChainThenThrowException() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);
			CallAdvisor notInChain = createMockAdvisor("notInChain", 3);

			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
				.pushAll(List.of(advisor1, advisor2))
				.build();

			assertThatThrownBy(() -> AdvisorUtils.copyChainAfterAdvisor(chain, notInChain))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The specified advisor is not part of the chain")
				.hasMessageContaining("notInChain");
		}

		@Test
		void whenAdvisorIsLastInChainThenReturnEmptyChain() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);
			CallAdvisor advisor3 = createMockAdvisor("advisor3", 3);

			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
				.pushAll(List.of(advisor1, advisor2, advisor3))
				.build();

			CallAdvisorChain newChain = AdvisorUtils.copyChainAfterAdvisor(chain, advisor3);

			assertThat(newChain.getCallAdvisors()).isEmpty();
		}

		@Test
		void whenAdvisorIsFirstInChainThenReturnChainWithRemainingAdvisors() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);
			CallAdvisor advisor3 = createMockAdvisor("advisor3", 3);

			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
				.pushAll(List.of(advisor1, advisor2, advisor3))
				.build();

			CallAdvisorChain newChain = AdvisorUtils.copyChainAfterAdvisor(chain, advisor1);

			assertThat(newChain.getCallAdvisors()).hasSize(2);
			assertThat(newChain.getCallAdvisors().get(0).getName()).isEqualTo("advisor2");
			assertThat(newChain.getCallAdvisors().get(1).getName()).isEqualTo("advisor3");
		}

		@Test
		void whenAdvisorIsInMiddleOfChainThenReturnChainWithRemainingAdvisors() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);
			CallAdvisor advisor3 = createMockAdvisor("advisor3", 3);
			CallAdvisor advisor4 = createMockAdvisor("advisor4", 4);

			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
				.pushAll(List.of(advisor1, advisor2, advisor3, advisor4))
				.build();

			CallAdvisorChain newChain = AdvisorUtils.copyChainAfterAdvisor(chain, advisor2);

			assertThat(newChain.getCallAdvisors()).hasSize(2);
			assertThat(newChain.getCallAdvisors().get(0).getName()).isEqualTo("advisor3");
			assertThat(newChain.getCallAdvisors().get(1).getName()).isEqualTo("advisor4");
		}

		@Test
		void whenCopyingChainThenOriginalChainRemainsUnchanged() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);
			CallAdvisor advisor3 = createMockAdvisor("advisor3", 3);

			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
				.pushAll(List.of(advisor1, advisor2, advisor3))
				.build();

			CallAdvisorChain newChain = AdvisorUtils.copyChainAfterAdvisor(chain, advisor1);

			// Original chain should still have all advisors
			assertThat(chain.getCallAdvisors()).hasSize(3);
			assertThat(chain.getCallAdvisors().get(0).getName()).isEqualTo("advisor1");
			assertThat(chain.getCallAdvisors().get(1).getName()).isEqualTo("advisor2");
			assertThat(chain.getCallAdvisors().get(2).getName()).isEqualTo("advisor3");

			// New chain should only have remaining advisors
			assertThat(newChain.getCallAdvisors()).hasSize(2);
			assertThat(newChain.getCallAdvisors().get(0).getName()).isEqualTo("advisor2");
			assertThat(newChain.getCallAdvisors().get(1).getName()).isEqualTo("advisor3");
		}

		@Test
		void whenCopyingChainThenObservationRegistryIsPreserved() {
			CallAdvisor advisor1 = createMockAdvisor("advisor1", 1);
			CallAdvisor advisor2 = createMockAdvisor("advisor2", 2);

			ObservationRegistry customRegistry = ObservationRegistry.create();
			CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(customRegistry)
				.pushAll(List.of(advisor1, advisor2))
				.build();

			CallAdvisorChain newChain = AdvisorUtils.copyChainAfterAdvisor(chain, advisor1);

			assertThat(newChain.getObservationRegistry()).isSameAs(customRegistry);
		}

		private CallAdvisor createMockAdvisor(String name, int order) {
			return new CallAdvisor() {
				@Override
				public String getName() {
					return name;
				}

				@Override
				public int getOrder() {
					return order;
				}

				@Override
				public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
					return chain.nextCall(request);
				}
			};
		}

	}

}
