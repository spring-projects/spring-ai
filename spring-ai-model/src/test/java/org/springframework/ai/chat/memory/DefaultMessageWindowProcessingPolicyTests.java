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

package org.springframework.ai.chat.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultMessageWindowProcessingPolicy}.
 *
 * @author Thomas Vitale
 */
public class DefaultMessageWindowProcessingPolicyTests {

	private final MessageWindowProcessingPolicy processingPolicy = new DefaultMessageWindowProcessingPolicy();

	@Test
	void noEvictionWhenMessagesWithinLimit() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new UserMessage("Hello"), new AssistantMessage("Hi there")));
		List<Message> newMessages = new ArrayList<>(List.of(new UserMessage("How are you?")));
		int limit = 3;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new UserMessage("Hello"), new AssistantMessage("Hi there"),
				new UserMessage("How are you?"));
	}

	@Test
	void evictionWhenMessagesExceedLimit() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		int limit = 2;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).hasSize(2);
		assertThat(result).containsExactly(new UserMessage("Message 2"), new AssistantMessage("Response 2"));
	}

	@Test
	void systemMessageIsPreserved() {
		List<Message> historyMessages = new ArrayList<>(List.of(new SystemMessage("System instruction"),
				new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		int limit = 3;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new SystemMessage("System instruction"), new UserMessage("Message 2"),
				new AssistantMessage("Response 2"));
	}

	@Test
	void multipleSystemMessagesArePreserved() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2"),
						new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		int limit = 3;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new SystemMessage("System instruction 1"),
				new SystemMessage("System instruction 2"), new AssistantMessage("Response 2"));
	}

	@Test
	void emptyMessageList() {
		List<Message> historyMessages = new ArrayList<>();
		List<Message> newMessages = new ArrayList<>();
		int limit = 5;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).isEmpty();
	}

	@Test
	void zeroLimitNotAllowed() {
		List<Message> historyMessages = new ArrayList<>(List.of(new UserMessage("Message 1")));
		List<Message> newMessages = new ArrayList<>(List.of(new AssistantMessage("Response 1")));

		assertThatThrownBy(() -> processingPolicy.process(historyMessages, newMessages, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("limit must be greater than 0");
	}

	@Test
	void negativeLimitNotAllowed() {
		List<Message> historyMessages = new ArrayList<>(List.of(new UserMessage("Message 1")));
		List<Message> newMessages = new ArrayList<>(List.of(new AssistantMessage("Response 1")));

		assertThatThrownBy(() -> processingPolicy.process(historyMessages, newMessages, -1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("limit must be greater than 0");
	}

	@Test
	void oldSystemMessagesAreRemovedEvenWithCountLessThanLimit() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2")));
		List<Message> newMessages = new ArrayList<>(List.of(new SystemMessage("System instruction 3")));
		int limit = 2;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		// Old system messages are moved if a new one is provided, even if there's room in
		// the window.
		assertThat(result).hasSize(1);
		assertThat(result).containsExactly(new SystemMessage("System instruction 3"));
	}

	@Test
	void mixedMessagesWithLimitEqualToSystemMessageCount() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2")));
		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		int limit = 2;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(result).hasSize(2);
		assertThat(result).containsExactly(new SystemMessage("System instruction 1"),
				new SystemMessage("System instruction 2"));
	}

	@Test
	void originalListIsNotModified() {
		List<Message> historyMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		List<Message> originalHistoryMessages = new ArrayList<>(historyMessages);
		List<Message> originalNewMessages = new ArrayList<>(newMessages);
		int limit = 4;

		List<Message> result = processingPolicy.process(historyMessages, newMessages, limit);

		assertThat(historyMessages).isEqualTo(originalHistoryMessages);
		assertThat(newMessages).isEqualTo(originalNewMessages);
		assertThat(result).isNotSameAs(historyMessages);
		assertThat(result).isNotSameAs(newMessages);
	}

}
