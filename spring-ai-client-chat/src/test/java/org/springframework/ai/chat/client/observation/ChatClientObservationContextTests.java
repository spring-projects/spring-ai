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
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ChatClientObservationContext}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
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

}
