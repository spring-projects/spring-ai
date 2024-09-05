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

package org.springframework.ai.chat.client.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.AroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class AdvisorsTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	public class MockAroundAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

		public AdvisedRequest advisedRequest;

		public AdvisedResponse advisedResponse;

		public List<AdvisedResponse> aroundAdvisedResponses = new ArrayList<>();

		@Override
		public String getName() {
			return "MockAroundAdvisor";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, AroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundCallBefore", "AROUND_CALL_BEFORE");
				return context;
			});

			AdvisedResponse advisedResponse = this.advisedResponse = chain.nextAroundCall(this.advisedRequest);

			this.advisedResponse = advisedResponse.updateContext(context -> {
				context.put("aroundCallAfter", "AROUND_CALL_AFTER");
				return context;
			});

			return this.advisedResponse;
		}

		@Override
		public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, AroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundStreamBefore", "AROUND_STREAM_BEFORE");
				return context;
			});

			Flux<AdvisedResponse> advisedResponseStream = chain.nextAroundStream(this.advisedRequest);

			return advisedResponseStream.map(advisedResponse -> {
				return advisedResponse.updateContext(context -> {
					context.put("aroundStreamAfter", "AROUND_STREAM_AFTER");
					return context;
				});
			}).doOnNext(ar -> this.aroundAdvisedResponses.add(ar));

		}

	}

	@Test
	public void callAdvisorsContextPropagation() {

		var mockAroundAdvisor = new MockAroundAdvisor();

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John")))))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your name is John")))));

		var chatClient = ChatClient.builder(chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockAroundAdvisor)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.call()
			.content();

		assertThat(content).isEqualTo("Hello John");

		// AROUND
		assertThat(mockAroundAdvisor.advisedResponse.response()).isNotNull();
		assertThat(mockAroundAdvisor.advisedResponse.adviseContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("aroundCallBefore", "AROUND_CALL_BEFORE")
			.containsEntry("aroundCallAfter", "AROUND_CALL_AFTER");
	}

	@Test
	public void streamAdvisorsContextPropagation() {

		var mockAroundAdvisor = new MockAroundAdvisor();

		when(chatModel.stream(promptCaptor.capture()))
			.thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
					new ChatResponse(List.of(new Generation(new AssistantMessage(" John"))))));

		var chatClient = ChatClient.builder(chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockAroundAdvisor)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.stream()
			.content()
			.collectList()
			.block()
			.stream()
			.collect(Collectors.joining());

		assertThat(content).isEqualTo("Hello John");

		// AROUND
		assertThat(mockAroundAdvisor.aroundAdvisedResponses).isNotEmpty();

		mockAroundAdvisor.aroundAdvisedResponses.stream().forEach(advisedResponse -> {
			assertThat(advisedResponse.adviseContext()).containsEntry("key1", "value1")
				.containsEntry("key2", "value2")
				.containsEntry("aroundStreamBefore", "AROUND_STREAM_BEFORE")
				.containsEntry("aroundStreamAfter", "AROUND_STREAM_AFTER");
		});
	}

}
