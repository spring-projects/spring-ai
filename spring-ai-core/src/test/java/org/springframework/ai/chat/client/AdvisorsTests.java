/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.AfterAdvisor;
import org.springframework.ai.chat.client.advisor.api.BeforeAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class AdvisorsTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	public static class MockBeforeAdvisor implements BeforeAdvisor {

		public AdvisedRequest advisedRequest;

		@Override
		public String getName() {
			return "MockBeforeAdvisor";
		}

		@Override
		public AdvisedRequest before(AdvisedRequest advisedRequest) {
			this.advisedRequest = advisedRequest.contextTransform(context -> {
				context.put("adviseRequest", "adviseRequest");
				return context;
			});

			return this.advisedRequest;
		}

	}

	public static class MockAfterAdvisor implements AfterAdvisor {

		public AdvisedResponse advisedResponse;

		public Flux<AdvisedResponse> advisedResponseStream;

		@Override
		public String getName() {
			return "MockAfterAdvisor";
		}

		@Override
		public AdvisedResponse afterCall(AdvisedResponse advisedResponse) {
			this.advisedResponse = advisedResponse.contextTransform(context -> {
				context.put("adviseResponse", "adviseResponse");
				return context;
			});
			return this.advisedResponse;
		}

		@Override
		public Flux<AdvisedResponse> afterStream(Flux<AdvisedResponse> advisedResponseStream) {

			this.advisedResponseStream = advisedResponseStream.map(advisedResponse -> {
				return advisedResponse.contextTransform(context -> {
					context.put("adviseResponse", "adviseResponse");
					return context;
				});
			});

			return this.advisedResponseStream;
		}

	}

	@Test
	public void advisors() {

		var mockBeforeAdvisor = new MockBeforeAdvisor();
		var mockAfterAdvisor = new MockAfterAdvisor();

		when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation("Hello John"))))
			.thenReturn(new ChatResponse(List.of(new Generation("Your name is John"))));

		when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation("Hello John"))))
			.thenReturn(new ChatResponse(List.of(new Generation("Your name is John"))));

		var chatClient = ChatClient.builder(chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockBeforeAdvisor, mockAfterAdvisor)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.call()
			.content();

		assertThat(content).isEqualTo("Hello John");

		assertThat(mockBeforeAdvisor.advisedRequest.adviseContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("adviseRequest", "adviseRequest");
		assertThat(mockBeforeAdvisor.advisedRequest.advisorParams()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.doesNotContainKey("adviseRequest");

		assertThat(mockAfterAdvisor.advisedResponse.adviseContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("adviseRequest", "adviseRequest")
			.containsEntry("adviseResponse", "adviseResponse");
		assertThat(mockAfterAdvisor.advisedResponse.response()).isNotNull();
	}

}
