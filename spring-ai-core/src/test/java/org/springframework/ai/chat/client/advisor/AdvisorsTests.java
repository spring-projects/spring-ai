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
import static org.mockito.Mockito.verify;

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
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
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

		private final String name;

		private final int order;

		public MockAroundAdvisor(String name, int order) {
			this.name = name;
			this.order = order;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int getOrder() {
			return order;
		}

		@Override
		public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundCallBefore" + getName(), "AROUND_CALL_BEFORE " + getName());
				context.put("lastBefore", getName());
				return context;
			});

			AdvisedResponse advisedResponse = this.advisedResponse = chain.nextAroundCall(this.advisedRequest);

			this.advisedResponse = advisedResponse.updateContext(context -> {
				context.put("aroundCallAfter" + name, "AROUND_CALL_AFTER " + name);
				context.put("lastAfter", name);
				return context;
			});

			return this.advisedResponse;
		}

		@Override
		public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

			this.advisedRequest = advisedRequest.updateContext(context -> {
				context.put("aroundStreamBefore" + name, "AROUND_STREAM_BEFORE " + name);
				context.put("lastBefore", name);
				return context;
			});

			Flux<AdvisedResponse> advisedResponseStream = chain.nextAroundStream(this.advisedRequest);

			return advisedResponseStream.map(advisedResponse -> {
				return advisedResponse.updateContext(context -> {
					context.put("aroundStreamAfter" + name, "AROUND_STREAM_AFTER " + name);
					context.put("lastAfter", name);
					return context;
				});
			}).doOnNext(ar -> this.aroundAdvisedResponses.add(ar));

		}

	}

	@Test
	public void callAdvisorsContextPropagation() {

		// Order==0 has higher priority thant order == 1. The lower the order the higher
		// the priority.
		var mockAroundAdvisor1 = new MockAroundAdvisor("Advisor1", 0);
		var mockAroundAdvisor2 = new MockAroundAdvisor("Advisor2", 1);

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John")))));

		var chatClient = ChatClient.builder(chatModel)
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

		verify(chatModel).call(promptCaptor.capture());
	}

	@Test
	public void streamAdvisorsContextPropagation() {

		var mockAroundAdvisor1 = new MockAroundAdvisor("Advisor1", 0);
		var mockAroundAdvisor2 = new MockAroundAdvisor("Advisor2", 1);

		when(chatModel.stream(promptCaptor.capture()))
			.thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
					new ChatResponse(List.of(new Generation(new AssistantMessage(" John"))))));

		var chatClient = ChatClient.builder(chatModel)
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

		mockAroundAdvisor1.aroundAdvisedResponses.stream().forEach(advisedResponse -> {
			assertThat(advisedResponse.adviseContext()).containsEntry("key1", "value1")
				.containsEntry("key2", "value2")
				.containsEntry("aroundStreamBeforeAdvisor1", "AROUND_STREAM_BEFORE Advisor1")
				.containsEntry("aroundStreamAfterAdvisor1", "AROUND_STREAM_AFTER Advisor1")
				.containsEntry("aroundStreamBeforeAdvisor2", "AROUND_STREAM_BEFORE Advisor2")
				.containsEntry("aroundStreamAfterAdvisor2", "AROUND_STREAM_AFTER Advisor2")
				.containsEntry("lastBefore", "Advisor2") // inner
				.containsEntry("lastAfter", "Advisor1"); // outer
		});

		verify(chatModel).stream(promptCaptor.capture());
	}

}
