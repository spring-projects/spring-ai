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

package org.springframework.ai.chat.client.advisor.observation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisorObservationContext}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class AdvisorObservationContextTests {

	@Test
	void whenMandatoryOptionsThenReturn() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.advisorName("AdvisorName")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void missingAdvisorName() {
		assertThatThrownBy(() -> AdvisorObservationContext.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorName cannot be null or empty");
	}

	@Test
	void whenBuilderWithAdvisedRequestThenReturn() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.advisorName("AdvisorName")
			.advisedRequest(mock(AdvisedRequest.class))
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenBuilderWithChatClientRequestThenReturn() {
		AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
			.advisorName("AdvisorName")
			.chatClientRequest(ChatClientRequest.builder().prompt(new Prompt()).build())
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void missingBuilderWithBothRequestsThenThrow() {
		assertThatThrownBy(() -> AdvisorObservationContext.builder()
			.advisedRequest(mock(AdvisedRequest.class))
			.chatClientRequest(ChatClientRequest.builder().prompt(new Prompt()).build())
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ChatClientRequest and AdvisedRequest cannot be set at the same time");
	}

}
