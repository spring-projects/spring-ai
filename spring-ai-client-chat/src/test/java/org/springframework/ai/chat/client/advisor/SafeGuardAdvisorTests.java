/*
 * Copyright 2025-2025 the original author or authors.
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
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SafeGuardAdvisor}.
 *
 * @author Sun Yuhan
 */
class SafeGuardAdvisorTests {

	private ChatClientRequest safeRequest;

	private ChatClientRequest unsafeRequest;

	@BeforeEach
	void setUp() {
		this.safeRequest = new ChatClientRequest(Prompt.builder().content("hello world").build(), Map.of());
		this.unsafeRequest = new ChatClientRequest(Prompt.builder().content("this contains secret").build(), Map.of());
	}

	@Test
	void constructorThrowsExceptionWhenSensitiveWordsIsNull() {
		assertThatThrownBy(() -> new SafeGuardAdvisor(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Sensitive words must not be null");
	}

	@Test
	void constructorThrowsExceptionWhenFailureResponseIsNull() {
		assertThatThrownBy(() -> new SafeGuardAdvisor(List.of("s"), null, 1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failure response must not be null");
	}

	@Test
	void adviseCallInterceptsWhenContainsSensitiveWord() {
		SafeGuardAdvisor advisor = new SafeGuardAdvisor(List.of("secret"));

		CallAdvisorChain mockChain = mock(CallAdvisorChain.class);

		ChatClientResponse response = advisor.adviseCall(this.unsafeRequest, mockChain);

		assertThat(response.chatResponse().getResult().getOutput().getText()).contains("I'm unable to respond");
		verify(mockChain, never()).nextCall(any());
	}

	@Test
	void adviseCallPassesThroughWhenNoSensitiveWord() {
		SafeGuardAdvisor advisor = new SafeGuardAdvisor(List.of("secret"));

		CallAdvisorChain mockChain = mock(CallAdvisorChain.class);
		ChatClientResponse expected = ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder().generations(List.of()).build())
			.context(Map.of())
			.build();

		when(mockChain.nextCall(this.safeRequest)).thenReturn(expected);

		ChatClientResponse response = advisor.adviseCall(this.safeRequest, mockChain);

		assertThat(response).isSameAs(expected);
		verify(mockChain).nextCall(this.safeRequest);
	}

	@Test
	void adviseStreamInterceptsWhenContainsSensitiveWord() {
		SafeGuardAdvisor advisor = new SafeGuardAdvisor(List.of("secret"));

		StreamAdvisorChain mockChain = mock(StreamAdvisorChain.class);

		Flux<ChatClientResponse> flux = advisor.adviseStream(this.unsafeRequest, mockChain);

		StepVerifier.create(flux)
			.assertNext(r -> assertThat(r.chatResponse().getResult().getOutput().getText())
				.contains("I'm unable to respond"))
			.verifyComplete();

		verify(mockChain, never()).nextStream(any());
	}

	@Test
	void adviseStreamPassesThroughWhenNoSensitiveWord() {
		SafeGuardAdvisor advisor = new SafeGuardAdvisor(List.of("secret"));

		StreamAdvisorChain mockChain = mock(StreamAdvisorChain.class);
		ChatClientResponse expected = ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder().generations(List.of()).build())
			.context(Map.of())
			.build();

		when(mockChain.nextStream(this.safeRequest)).thenReturn(Flux.just(expected));

		Flux<ChatClientResponse> flux = advisor.adviseStream(this.safeRequest, mockChain);

		StepVerifier.create(flux).expectNext(expected).verifyComplete();

		verify(mockChain).nextStream(this.safeRequest);
	}

	@Test
	void builderRespectsCustomValues() {
		SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
			.sensitiveWords(List.of("xxx"))
			.failureResponse("custom block")
			.order(42)
			.build();

		assertThat(advisor.getOrder()).isEqualTo(42);

		CallAdvisorChain mockChain = mock(CallAdvisorChain.class);
		ChatClientRequest req = new ChatClientRequest(Prompt.builder().content("xxx is here").build(), Map.of());

		ChatClientResponse response = advisor.adviseCall(req, mockChain);
		assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("custom block");
	}

	@Test
	void safeGuardAdvisorShouldAllowSubsequentValidMessagesAfterBlockingSensitiveContent() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		// Create three advisors
		SafeGuardAdvisor safeGuardAdvisor = new SafeGuardAdvisor(List.of("secret"));
		MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		ChatModelCallAdvisor chatModelCallAdvisor = mock(ChatModelCallAdvisor.class);

		when(chatModelCallAdvisor.adviseCall(any(), any())).thenReturn(ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage("Hello, how can I help?"))))
				.build())
			.build());
		when(chatModelCallAdvisor.getName()).thenReturn(ChatModelCallAdvisor.class.getSimpleName());
		when(chatModelCallAdvisor.getOrder()).thenReturn(Ordered.LOWEST_PRECEDENCE);

		var advisors = List.of(safeGuardAdvisor, memoryAdvisor, chatModelCallAdvisor);

		// Verify that SafeGuardAdvisor's order is higher than MessageChatMemoryAdvisor
		assertThat(safeGuardAdvisor.getOrder()).isLessThan(memoryAdvisor.getOrder());

		// Create a request containing sensitive words
		ChatClientRequest sensitiveRequest = new ChatClientRequest(
				Prompt.builder().content("this contains secret").build(), Map.of());

		// Create a normal request
		ChatClientRequest normalRequest = new ChatClientRequest(Prompt.builder().content("hello world").build(),
				Map.of());

		// Send a message with sensitive words - should be intercepted by SafeGuardAdvisor
		DefaultAroundAdvisorChain aroundAdvisorChain1 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(advisors)
			.build();

		ChatClientResponse response1 = aroundAdvisorChain1.nextCall(sensitiveRequest);
		assertThat(response1.chatResponse().getResult().getOutput().getText()).contains("I'm unable to respond");

		// Verify that sensitive messages are not added to chat memory (intercepted by
		// SafeGuardAdvisor)
		List<Message> memoryMessagesAfterSensitive = chatMemory.get(ChatMemory.DEFAULT_CONVERSATION_ID);
		assertThat(memoryMessagesAfterSensitive.size()).isEqualTo(0);

		// Send a normal message - should be processed normally
		DefaultAroundAdvisorChain aroundAdvisorChain2 = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(advisors)
			.build();

		ChatClientResponse response2 = aroundAdvisorChain2.nextCall(normalRequest);
		assertThat(response2.chatResponse().getResult().getOutput().getText()).isEqualTo("Hello, how can I help?");

		// Verify that chat memory contains only normal messages
		List<Message> memoryMessagesNormalRequest = chatMemory.get(ChatMemory.DEFAULT_CONVERSATION_ID);

		assertThat(memoryMessagesNormalRequest.size() == 2).isTrue();
		List<String> messageTexts = memoryMessagesNormalRequest.stream().map(Message::getText).toList();

		assertThat(messageTexts.contains("hello world")).isTrue();
		assertThat(messageTexts.contains("Hello, how can I help?")).isTrue();
	}

}
