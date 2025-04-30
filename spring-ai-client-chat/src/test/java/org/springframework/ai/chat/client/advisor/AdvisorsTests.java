/*
 * Copyright 2023-2024 the original author or authors.
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
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class AdvisorsTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	public void callAdvisorsContextPropagation() {

		// Order==0 has higher priority thant order == 1. The lower the order the higher
		// the priority.
		var mockAroundAdvisor1 = new MockAroundAdvisor("Advisor1", 0);
		var mockAroundAdvisor2 = new MockAroundAdvisor("Advisor2", 1);

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John")))));

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockAroundAdvisor1)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(mockAroundAdvisor2)
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.call()
			.content();

		assertThat(content).isEqualTo("Hello John");

		// AROUND
		assertThat(mockAroundAdvisor1.advisedResponse.response()).isNotNull();
		assertThat(mockAroundAdvisor1.advisedResponse.adviseContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("aroundCallBeforeAdvisor1", "AROUND_CALL_BEFORE Advisor1")
			.containsEntry("aroundCallAfterAdvisor1", "AROUND_CALL_AFTER Advisor1")
			.containsEntry("aroundCallBeforeAdvisor2", "AROUND_CALL_BEFORE Advisor2")
			.containsEntry("aroundCallAfterAdvisor2", "AROUND_CALL_AFTER Advisor2")
			.containsEntry("lastBefore", "Advisor2") // inner
			.containsEntry("lastAfter", "Advisor1"); // outer

		verify(this.chatModel).call(this.promptCaptor.capture());
	}

	@Test
	public void streamAdvisorsContextPropagation() {

		var mockAroundAdvisor1 = new MockAroundAdvisor("Advisor1", 0);
		var mockAroundAdvisor2 = new MockAroundAdvisor("Advisor2", 1);

		given(this.chatModel.stream(this.promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
					new ChatResponse(List.of(new Generation(new AssistantMessage(" John"))))));

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockAroundAdvisor1)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.advisors(mockAroundAdvisor2)
			.stream()
			.content()
			.collectList()
			.block()
			.stream()
			.collect(Collectors.joining());

		assertThat(content).isEqualTo("Hello John");

		// AROUND
		assertThat(mockAroundAdvisor1.aroundAdvisedResponses).isNotEmpty();

		mockAroundAdvisor1.aroundAdvisedResponses.stream()
			.forEach(advisedResponse -> assertThat(advisedResponse.adviseContext()).containsEntry("key1", "value1")
				.containsEntry("key2", "value2")
				.containsEntry("aroundStreamBeforeAdvisor1", "AROUND_STREAM_BEFORE Advisor1")
				.containsEntry("aroundStreamAfterAdvisor1", "AROUND_STREAM_AFTER Advisor1")
				.containsEntry("aroundStreamBeforeAdvisor2", "AROUND_STREAM_BEFORE Advisor2")
				.containsEntry("aroundStreamAfterAdvisor2", "AROUND_STREAM_AFTER Advisor2")
				.containsEntry("lastBefore", "Advisor2") // inner
				.containsEntry("lastAfter", "Advisor1") // outer
			);

		verify(this.chatModel).stream(this.promptCaptor.capture());
	}

	public class MockAroundAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

		private final String name;

		private final int order;

		public AdvisedRequest advisedRequest;

		public AdvisedResponse advisedResponse;

		public List<AdvisedResponse> aroundAdvisedResponses = new ArrayList<>();

		public MockAroundAdvisor(String name, int order) {
			this.name = name;
			this.order = order;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public int getOrder() {
			return this.order;
		}

		@Override
		public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundCallBefore" + getName(), "AROUND_CALL_BEFORE " + getName());
				context.put("lastBefore", getName());
				return context;
			});

			this.advisedResponse = chain.nextAroundCall(this.advisedRequest);
			AdvisedResponse advisedResponse = this.advisedResponse;

			this.advisedResponse = advisedResponse.updateContext(context -> {
				context.put("aroundCallAfter" + this.name, "AROUND_CALL_AFTER " + this.name);
				context.put("lastAfter", this.name);
				return context;
			});

			return this.advisedResponse;
		}

		@Override
		public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundStreamBefore" + this.name, "AROUND_STREAM_BEFORE " + this.name);
				context.put("lastBefore", this.name);
				return context;
			});

			Flux<AdvisedResponse> advisedResponseStream = chain.nextAroundStream(this.advisedRequest);

			return advisedResponseStream.map(advisedResponse -> {
				return advisedResponse.updateContext(context -> {
					context.put("aroundStreamAfter" + this.name, "AROUND_STREAM_AFTER " + this.name);
					context.put("lastAfter", this.name);
					return context;
				});
			}).doOnNext(ar -> this.aroundAdvisedResponses.add(ar));

		}

	}

}
