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

package org.springframework.ai.chat.client.observation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ChatClientObservationContext}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class ChatClientObservationContextTests {

	@Mock
	ChatModel chatModel;

	@Test
	void whenMandatoryRequestOptionsThenReturn() {
		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenNullAdvisorsThenReturn() {
		assertThatThrownBy(() -> ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(null)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("advisors cannot be null");
	}

	@Test
	void whenAdvisorsWithNullElementsThenReturn() {
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(mock(Advisor.class));
		advisors.add(null);
		assertThatThrownBy(() -> ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(advisors)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisors cannot contain null elements");
	}

	@Test
	void whenNullRequestThenThrowException() {
		assertThatThrownBy(() -> ChatClientObservationContext.builder().request(null).build())
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void whenValidAdvisorsListThenReturn() {
		List<Advisor> advisors = List.of(mock(Advisor.class), mock(Advisor.class));

		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(advisors)
			.build();

		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getAdvisors()).hasSize(2);
		// Check that advisors are present, but don't assume exact ordering or same
		// instances
		assertThat(observationContext.getAdvisors()).isNotNull().isNotEmpty();
	}

	@Test
	void whenAdvisorsModifiedAfterBuildThenContextMayBeUnaffected() {
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(mock(Advisor.class));

		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(advisors)
			.build();

		int originalSize = observationContext.getAdvisors().size();

		// Try to modify original list
		advisors.add(mock(Advisor.class));

		// Check if context is affected or not - both are valid implementations
		int currentSize = observationContext.getAdvisors().size();
		// Defensive copy was made
		// Same reference used
		assertThat(currentSize).satisfiesAnyOf(size -> assertThat(size).isEqualTo(originalSize),
				size -> assertThat(size).isEqualTo(originalSize + 1));
	}

	@Test
	void whenGetAdvisorsCalledThenReturnsValidCollection() {
		List<Advisor> advisors = List.of(mock(Advisor.class));

		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(advisors)
			.build();

		var returnedAdvisors = observationContext.getAdvisors();

		// Just verify we get a valid collection back, using var to handle any return type
		assertThat(returnedAdvisors).isNotNull();
		assertThat(returnedAdvisors).hasSize(1);
	}

	@Test
	void whenRequestWithNullPromptThenThrowException() {
		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(null).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenEmptyAdvisorsListThenReturn() {
		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.advisors(List.of())
			.build();

		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getAdvisors()).isEmpty();
	}

	@Test
	void whenGetRequestThenReturnsSameInstance() {
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("Test prompt")).build();

		var observationContext = ChatClientObservationContext.builder().request(request).build();

		assertThat(observationContext.getRequest()).isEqualTo(request);
		assertThat(observationContext.getRequest()).isSameAs(request);
	}

	@Test
	void whenBuilderReusedThenReturnDifferentInstances() {
		var builder = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build());

		var context1 = builder.build();
		var context2 = builder.build();

		assertThat(context1).isNotSameAs(context2);
	}

	@Test
	void whenNoAdvisorsSpecifiedThenGetAdvisorsReturnsEmptyOrNull() {
		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt()).build())
			.build();

		// Should return either empty list or null when no advisors specified
		assertThat(observationContext.getAdvisors()).satisfiesAnyOf(advisors -> assertThat(advisors).isNull(),
				advisors -> assertThat(advisors).isEmpty());
	}

	@Test
	void whenSetChatClientResponseThenReturnTheSameResponse() {
		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt("Test prompt")).build())
			.build();
		var response = ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage("Test message"))))
				.build())
			.build();

		observationContext.setResponse(response);
		assertThat(observationContext.getResponse()).isSameAs(response);
	}

	@Test
	void whenSetChatClientResponseWithNullChatResponseThenReturnNull() {
		var observationContext = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt("Test prompt")).build())
			.build();

		observationContext.setResponse(null);
		assertThat(observationContext.getResponse()).isNull();
	}

}
