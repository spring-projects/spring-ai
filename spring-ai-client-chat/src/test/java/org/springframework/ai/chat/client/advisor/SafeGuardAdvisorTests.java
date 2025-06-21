/*
 * Copyright 2025 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author YunKui Lu
 */
@ExtendWith(MockitoExtension.class)
class SafeGuardAdvisorTests {

	@Mock
	ChatModel chatModel;

	@Test
	void whenSensitiveWordsIsNullThenThrow() {
		assertThatThrownBy(() -> SafeGuardAdvisor.builder().sensitiveWords(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Sensitive words must not be null!");
	}

	@Test
	void whenFailureResponseIsNullThenThrow() {
		assertThatThrownBy(() -> SafeGuardAdvisor.builder().sensitiveWords(List.of()).failureResponse(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failure response must not be null!");
	}

	@Test
	void testBuilderMethodChaining() {
		// Test builder method chaining with methods from AbstractBuilder and
		// SafeGuardAdvisor.Builder
		List<String> sensitiveWords = List.of("word1", "word2");
		int customOrder = 42;
		String failureResponse = "That topic may be too sensitive to discuss. Can we talk about something else instead?";

		SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
			.sensitiveWords(sensitiveWords)
			.failureResponse(failureResponse)
			.order(customOrder)
			.build();

		// Verify the advisor was built with the correct properties
		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	@Test
	void testDefaultValues() {
		// Test builder method chaining with methods from AbstractBuilder and
		// SafeGuardAdvisor.Builder
		List<String> sensitiveWords = List.of("word1", "word2");
		String failureResponse = "That topic may be too sensitive to discuss. Can we talk about something else instead?";

		SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
			.sensitiveWords(sensitiveWords)
			.failureResponse(failureResponse)
			.build();

		// Verify default values
		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isZero();
	}

	@Test
	void callAdvisorsContextPropagation() {
		List<String> sensitiveWords = List.of("word1", "word2");
		String failureResponse = "That topic may be too sensitive to discuss. Can we talk about something else instead?";

		SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
			.sensitiveWords(sensitiveWords)
			.failureResponse(failureResponse)
			.build();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(advisor)
			.build();

		var chatClientResponse = chatClient.prompt()
			// should be case-insensitive
			.user("do you like Word1?")
			.advisors(advisor)
			.call()
			.chatClientResponse();

		assertThat(chatClientResponse.chatResponse()).isNotNull();
		assertThat(chatClientResponse.chatResponse().getResult().getOutput().getText()).isEqualTo(failureResponse);
		assertThat((List<String>) chatClientResponse.context().get(SafeGuardAdvisor.CONTAINS_SENSITIVE_WORDS))
			.containsExactly("word1");
	}

	@Test
	void streamAdvisorsContextPropagation() {
		List<String> sensitiveWords = List.of("Word1", "Word2");
		String failureResponse = "That topic may be too sensitive to discuss. Can we talk about something else instead?";

		SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
			.sensitiveWords(sensitiveWords)
			.failureResponse(failureResponse)
			.build();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(advisor)
			.build();

		var chatClientResponse = chatClient.prompt()
			// should be case-insensitive
			.user("do you like word2?")
			.advisors(advisor)
			.stream()
			.chatClientResponse()
			.blockFirst();

		assertThat(chatClientResponse.chatResponse()).isNotNull();
		assertThat(chatClientResponse.chatResponse().getResult().getOutput().getText()).isEqualTo(failureResponse);
		assertThat((List<String>) chatClientResponse.context().get(SafeGuardAdvisor.CONTAINS_SENSITIVE_WORDS))
			.containsExactly("Word2");
	}

}
