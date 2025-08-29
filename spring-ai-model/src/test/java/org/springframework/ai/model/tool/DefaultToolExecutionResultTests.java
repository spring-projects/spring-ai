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

package org.springframework.ai.model.tool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultToolExecutionResult}.
 *
 * @author Thomas Vitale
 */
class DefaultToolExecutionResultTests {

	@Test
	void whenConversationHistoryIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot be null");
	}

	@Test
	void whenConversationHistoryHasNullElementsThenThrow() {
		var history = new ArrayList<Message>();
		history.add(null);
		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(history).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot contain null elements");
	}

	@Test
	void builder() {
		var conversationHistory = new ArrayList<Message>();
		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();
		assertThat(result.conversationHistory()).isEqualTo(conversationHistory);
		assertThat(result.returnDirect()).isTrue();
	}

	@Test
	void whenBuilderWithMinimalRequiredFields() {
		var conversationHistory = new ArrayList<Message>();
		var result = DefaultToolExecutionResult.builder().conversationHistory(conversationHistory).build();

		assertThat(result.conversationHistory()).isEqualTo(conversationHistory);
		assertThat(result.returnDirect()).isFalse(); // Default value should be false
	}

	@Test
	void whenBuilderWithReturnDirectFalse() {
		var conversationHistory = new ArrayList<Message>();
		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(false)
			.build();

		assertThat(result.conversationHistory()).isEqualTo(conversationHistory);
		assertThat(result.returnDirect()).isFalse();
	}

	@Test
	void whenConversationHistoryIsEmpty() {
		var conversationHistory = new ArrayList<Message>();
		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();

		assertThat(result.conversationHistory()).isEmpty();
		assertThat(result.returnDirect()).isTrue();
	}

	@Test
	void whenConversationHistoryHasMultipleMessages() {
		var conversationHistory = new ArrayList<Message>();
		var message1 = new org.springframework.ai.chat.messages.UserMessage("Hello");
		var message2 = new org.springframework.ai.chat.messages.AssistantMessage("Hi there!");
		conversationHistory.add(message1);
		conversationHistory.add(message2);

		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(false)
			.build();

		assertThat(result.conversationHistory()).hasSize(2);
		assertThat(result.conversationHistory()).containsExactly(message1, message2);
		assertThat(result.returnDirect()).isFalse();
	}

	@Test
	void whenConversationHistoryHasNullElementsInMiddle() {
		var history = new ArrayList<Message>();
		history.add(new org.springframework.ai.chat.messages.UserMessage("First message"));
		history.add(null);
		history.add(new org.springframework.ai.chat.messages.AssistantMessage("Last message"));

		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(history).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot contain null elements");
	}

	@Test
	void whenConversationHistoryHasMultipleNullElements() {
		var history = new ArrayList<Message>();
		history.add(null);
		history.add(null);
		history.add(new org.springframework.ai.chat.messages.UserMessage("Valid message"));

		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(history).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot contain null elements");
	}

	@Test
	void whenBuilderIsReused() {
		var conversationHistory1 = new ArrayList<Message>();
		conversationHistory1.add(new org.springframework.ai.chat.messages.UserMessage("Message 1"));

		var conversationHistory2 = new ArrayList<Message>();
		conversationHistory2.add(new org.springframework.ai.chat.messages.UserMessage("Message 2"));

		var builder = DefaultToolExecutionResult.builder();

		var result1 = builder.conversationHistory(conversationHistory1).returnDirect(true).build();

		var result2 = builder.conversationHistory(conversationHistory2).returnDirect(false).build();

		assertThat(result1.conversationHistory()).isEqualTo(conversationHistory1);
		assertThat(result1.returnDirect()).isTrue();
		assertThat(result2.conversationHistory()).isEqualTo(conversationHistory2);
		assertThat(result2.returnDirect()).isFalse();
	}

	@Test
	void whenConversationHistoryIsModifiedAfterBuilding() {
		var conversationHistory = new ArrayList<Message>();
		var originalMessage = new org.springframework.ai.chat.messages.UserMessage("Original");
		conversationHistory.add(originalMessage);

		var result = DefaultToolExecutionResult.builder().conversationHistory(conversationHistory).build();

		// Modify the original list after building
		conversationHistory.add(new org.springframework.ai.chat.messages.AssistantMessage("Added later"));

		// The result should reflect the modification if the same list reference is used
		// This tests whether the builder stores a reference or creates a copy
		assertThat(result.conversationHistory()).hasSize(2);
		assertThat(result.conversationHistory().get(0)).isEqualTo(originalMessage);
	}

	@Test
	void whenEqualsAndHashCodeAreConsistent() {
		var conversationHistory = new ArrayList<Message>();
		conversationHistory.add(new org.springframework.ai.chat.messages.UserMessage("Test message"));

		var result1 = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();

		var result2 = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();

		assertThat(result1).isEqualTo(result2);
		assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
	}

	@Test
	void whenConversationHistoryIsImmutableList() {
		List<Message> conversationHistory = List.of(new org.springframework.ai.chat.messages.UserMessage("Hello"),
				new org.springframework.ai.chat.messages.UserMessage("Hi!"));

		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(false)
			.build();

		assertThat(result.conversationHistory()).hasSize(2);
		assertThat(result.conversationHistory()).isEqualTo(conversationHistory);
	}

	@Test
	void whenReturnDirectIsChangedMultipleTimes() {
		var conversationHistory = new ArrayList<Message>();
		conversationHistory.add(new org.springframework.ai.chat.messages.UserMessage("Test"));

		var builder = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.returnDirect(false)
			.returnDirect(true);

		var result = builder.build();

		assertThat(result.returnDirect()).isTrue();
	}

}
