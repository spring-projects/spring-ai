/*
 * Copyright 2023-present the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolCallSanitizingChatMemory}. Covers the issue #6340
 * sanitization contract: strip tool round-trips, keep final assistant text.
 *
 * @author redinside-dev
 */
class ToolCallSanitizingChatMemoryTests {

	@Test
	void whenDelegateIsNullThenThrow() {
		assertThatThrownBy(() -> new ToolCallSanitizingChatMemory(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("delegate ChatMemory cannot be null");
	}

	@Test
	void getDelegateReturnsUnderlyingMemory() {
		ChatMemory delegate = MessageWindowChatMemory.builder().build();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);
		assertThat(memory.getDelegate()).isSameAs(delegate);
	}

	@Test
	void sanitizeDropsToolResponseMessages() {
		List<Message> input = List.of(new UserMessage("What time is it?"),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("id", "name", "{}")))
					.build(),
				new AssistantMessage("It's 10:34 AM"));
		List<Message> out = ToolCallSanitizingChatMemory.sanitize(input);
		assertThat(out).extracting(Message::getMessageType).containsExactly(MessageType.USER, MessageType.ASSISTANT);
	}

	@Test
	void sanitizeStripsToolCallsFromAssistantMessageButKeepsText() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(toolCall))
			.build();
		List<Message> input = List.of(new UserMessage("What time is it?"), withToolCalls);
		List<Message> out = ToolCallSanitizingChatMemory.sanitize(input);
		assertThat(out).hasSize(2);
		AssistantMessage sanitized = (AssistantMessage) out.get(1);
		assertThat(sanitized.getText()).isEqualTo("It's 10:34 AM");
		assertThat(sanitized.hasToolCalls()).isFalse();
	}

	@Test
	void sanitizeDropsPureToolCallAssistantMessageWithNoText() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage pureToolCall = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
		List<Message> input = List.of(new UserMessage("What time is it?"), pureToolCall,
				new AssistantMessage("It's 10:34 AM"));
		List<Message> out = ToolCallSanitizingChatMemory.sanitize(input);
		assertThat(out).hasSize(2);
		assertThat(out.get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(((AssistantMessage) out.get(1)).getText()).isEqualTo("It's 10:34 AM");
	}

	@Test
	void sanitizePreservesSystemAndUserMessages() {
		List<Message> input = List.of(new SystemMessage("You are helpful."), new UserMessage("Hi"));
		List<Message> out = ToolCallSanitizingChatMemory.sanitize(input);
		assertThat(out).hasSize(2);
		assertThat(out.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(out.get(1)).isInstanceOf(UserMessage.class);
	}

	@Test
	void sanitizeOnEmptyAndNullReturnsEmpty() {
		assertThat(ToolCallSanitizingChatMemory.sanitize(List.of())).isEmpty();
		assertThat(ToolCallSanitizingChatMemory.sanitize(null)).isEmpty();
	}

	@Test
	void addWithListSanitizesBeforePersisting() {
		InMemoryChatMemoryRepository repo = new InMemoryChatMemoryRepository();
		ChatMemory delegate = MessageWindowChatMemory.builder().chatMemoryRepository(repo).build();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(toolCall))
			.build();
		memory.add("c1",
				List.of(new UserMessage("What time is it?"), ToolResponseMessage.builder()
					.responses(
							List.of(new ToolResponseMessage.ToolResponse("id-1", "getDateTime", "2026-06-09T10:34:00")))
					.build(), withToolCalls));

		List<Message> persisted = memory.get("c1");
		assertThat(persisted).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT);
		AssistantMessage assistant = (AssistantMessage) persisted.get(1);
		assertThat(assistant.getText()).isEqualTo("It's 10:34 AM");
		assertThat(assistant.hasToolCalls()).isFalse();
	}

	@Test
	void addWithSingleMessageSanitizesAndDropsEmptyResult() {
		// Recording repository that fails on saveAll with an empty list, to prove we
		// never hand one to the delegate.
		StrictRepository strict = new StrictRepository();
		ChatMemory delegate = MessageWindowChatMemory.builder().chatMemoryRepository(strict).build();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		// A pure tool-call assistant message has no text after sanitization, so it
		// must be dropped.
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage pureToolCall = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
		memory.add("c1", pureToolCall);

		assertThat(strict.saveAllCalls).isEmpty();
		assertThat(memory.get("c1")).isEmpty();
	}

	@Test
	void getDelegatesAndSanitizes() {
		// Pre-populate the delegate directly with a malformed transcript (mimicking the
		// pre-fix behaviour where the memory advisor persisted a single AssistantMessage
		// carrying tool_calls without a following ToolResponseMessage).
		InMemoryChatMemoryRepository repo = new InMemoryChatMemoryRepository();
		ChatMemory delegate = MessageWindowChatMemory.builder().chatMemoryRepository(repo).build();
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage malformed = AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(toolCall))
			.build();
		delegate.add("c1", List.of(new UserMessage("What time is it?"), malformed));

		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);
		List<Message> out = memory.get("c1");
		assertThat(out).extracting(Message::getMessageType).containsExactly(MessageType.USER, MessageType.ASSISTANT);
		AssistantMessage assistant = (AssistantMessage) out.get(1);
		assertThat(assistant.getText()).isEqualTo("It's 10:34 AM");
		assertThat(assistant.hasToolCalls()).isFalse();
	}

	@Test
	void clearDelegates() {
		InMemoryChatMemoryRepository repo = new InMemoryChatMemoryRepository();
		ChatMemory delegate = MessageWindowChatMemory.builder().chatMemoryRepository(repo).build();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);
		memory.add("c1", new UserMessage("hi"));
		memory.clear("c1");
		assertThat(memory.get("c1")).isEmpty();
	}

	@Test
	void sanitizePreservesAssistantMessageMetadataAndMedia() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id-1", "function", "getDateTime", "{}");
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(toolCall))
			.properties(Map.of("reasoningContent", "the user asked for the time"))
			.build();
		List<Message> out = ToolCallSanitizingChatMemory.sanitize(List.of(withToolCalls));
		assertThat(out).hasSize(1);
		AssistantMessage sanitized = (AssistantMessage) out.get(0);
		assertThat(sanitized.getMetadata()).containsEntry("reasoningContent", "the user asked for the time");
		assertThat(sanitized.getText()).isEqualTo("It's 10:34 AM");
		assertThat(sanitized.hasToolCalls()).isFalse();
	}

	/**
	 * ChatMemoryRepository wrapper that records every saveAll call and fails on
	 * {@code saveAll(conversationId, [])} — used to prove that
	 * {@link ToolCallSanitizingChatMemory#add(String, Message)} never hands an empty list
	 * to the delegate.
	 */
	private static final class StrictRepository implements ChatMemoryRepository {

		final List<List<Message>> saveAllCalls = new java.util.ArrayList<>();

		@Override
		public List<String> findConversationIds() {
			return List.of();
		}

		@Override
		public List<Message> findByConversationId(String conversationId) {
			return List.of();
		}

		@Override
		public void saveAll(String conversationId, List<Message> messages) {
			if (messages == null || messages.isEmpty()) {
				throw new IllegalArgumentException("saveAll must not be called with empty list");
			}
			this.saveAllCalls.add(messages);
		}

		@Override
		public void deleteByConversationId(String conversationId) {
		}

	}

}
