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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ToolCallSanitizingChatMemory}.
 *
 * @author ENG
 */
class ToolCallSanitizingChatMemoryTests {

	private static final String CONV = "conv-1";

	@Test
	void ctorRejectsNullDelegate() {
		assertThatThrownBy(() -> new ToolCallSanitizingChatMemory(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("delegate");
	}

	@Test
	void addDropsToolResponseMessages() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("What time is it?"));
		messages.add(ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getDateTime", "\"10:34 AM\"")))
			.build());
		memory.add(CONV, messages);

		assertThat(delegate.saved).hasSize(1);
		assertThat(delegate.saved.get(0).get(0)).isInstanceOf(UserMessage.class);
	}

	@Test
	void addStripsToolCallsFromAssistantMessages() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		AssistantMessage assistantWithToolCalls = AssistantMessage.builder()
			.content("") // empty text + non-empty tool calls => drop entirely
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();
		AssistantMessage assistantWithTextAndToolCalls = AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();

		memory.add(CONV, List.of(assistantWithToolCalls, assistantWithTextAndToolCalls));

		assertThat(delegate.saved).hasSize(1);
		List<Message> persisted = delegate.saved.get(0);
		assertThat(persisted).hasSize(1);
		assertThat(persisted.get(0)).isInstanceOf(AssistantMessage.class);
		AssistantMessage kept = (AssistantMessage) persisted.get(0);
		assertThat(kept.getText()).isEqualTo("It's 10:34 AM");
		assertThat(kept.hasToolCalls()).isFalse();
		assertThat(kept.getToolCalls()).isEmpty();
	}

	@Test
	void addDropsEmptyAfterSanitizeListWithoutCallingDelegate() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		// Only messages that vanish under sanitization (a ToolResponseMessage and a
		// text-less assistant tool-calls message).
		List<Message> messages = new ArrayList<>();
		messages.add(ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getDateTime", "{}")))
			.build());
		messages.add(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build());

		memory.add(CONV, messages);

		assertThat(delegate.saved).as("delegate must NOT be called with an empty list").isEmpty();
	}

	@Test
	void addSingleMessageConvenienceSanitizes() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		// Convenience overload: default ChatMemory.add(conv, single) calls add(conv,
		// List.of(single)). Must not skip sanitization.
		ToolResponseMessage tool = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getDateTime", "{}")))
			.build();
		memory.add(CONV, tool);

		assertThat(delegate.saved).isEmpty();
	}

	@Test
	void addPreservesPlainUserAssistantAndSystemMessages() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		List<Message> messages = List.of(new UserMessage("hi"), AssistantMessage.builder().content("hello").build(),
				new org.springframework.ai.chat.messages.SystemMessage("be helpful"));
		memory.add(CONV, messages);

		assertThat(delegate.saved).hasSize(1);
		assertThat(delegate.saved.get(0)).hasSize(3);
	}

	@Test
	void getSanitizesOnRead() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		// Seed the underlying store with a "dirty" transcript that includes a tool
		// round-trip. Reads must sanitize it.
		List<Message> dirty = new ArrayList<>();
		dirty.add(new UserMessage("What time is it?"));
		dirty.add(AssistantMessage.builder()
			.content("It's 10:34 AM")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build());
		dirty.add(ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getDateTime", "\"10:34 AM\"")))
			.build());
		delegate.findByConversationIdResult = new ArrayList<>(dirty);

		List<Message> read = memory.get(CONV);

		assertThat(read).hasSize(2);
		assertThat(read.get(0)).isInstanceOf(UserMessage.class);
		assertThat(read.get(1)).isInstanceOf(AssistantMessage.class);
		AssistantMessage kept = (AssistantMessage) read.get(1);
		assertThat(kept.getText()).isEqualTo("It's 10:34 AM");
		assertThat(kept.hasToolCalls()).isFalse();
	}

	@Test
	void sanitizeIsIdempotent() {
		List<Message> dirty = List.of(new UserMessage("hi"),
				AssistantMessage.builder()
					.content("hello")
					.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "f", "{}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "f", "{}")))
					.build());

		List<Message> first = ToolCallSanitizingChatMemory.sanitize(dirty);
		List<Message> second = ToolCallSanitizingChatMemory.sanitize(first);

		assertThat(first).hasSize(2);
		assertThat(second).isEqualTo(first);
	}

	@Test
	void sanitizeHandlesNullAndEmptyInput() {
		assertThat(ToolCallSanitizingChatMemory.sanitize(null)).isEmpty();
		assertThat(ToolCallSanitizingChatMemory.sanitize(List.of())).isEmpty();
	}

	@Test
	void getDelegateExposesUnderlying() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);
		assertThat(memory.getDelegate()).isSameAs(delegate);
	}

	@Test
	void clearDelegates() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);
		memory.clear(CONV);
		assertThat(delegate.cleared).containsExactly(CONV);
	}

	@Test
	void metadataAndMediaPreservedOnStrippedAssistant() {
		RecordingChatMemory delegate = new RecordingChatMemory();
		ToolCallSanitizingChatMemory memory = new ToolCallSanitizingChatMemory(delegate);

		AssistantMessage src = AssistantMessage.builder()
			.content("hello")
			.properties(Map.of("k", "v"))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "f", "{}")))
			.build();

		memory.add(CONV, List.of(src));

		assertThat(delegate.saved).hasSize(1);
		AssistantMessage kept = (AssistantMessage) delegate.saved.get(0).get(0);
		assertThat(kept.getMetadata()).containsEntry("k", "v");
	}

	// --- recording delegate -------------------------------------------------

	/**
	 * Hand-rolled recording {@link ChatMemory} so we can assert what was passed through
	 * the decorator without depending on {@link MessageWindowChatMemory} or
	 * {@link InMemoryChatMemoryRepository} internals.
	 */
	private static final class RecordingChatMemory implements ChatMemory {

		final List<List<Message>> saved = new ArrayList<>();

		final List<String> cleared = new ArrayList<>();

		List<Message> findByConversationIdResult = List.of();

		@Override
		public void add(String conversationId, List<Message> messages) {
			this.saved.add(new ArrayList<>(messages));
		}

		@Override
		public List<Message> get(String conversationId) {
			return this.findByConversationIdResult == null ? List.of()
					: new ArrayList<>(this.findByConversationIdResult);
		}

		@Override
		public void clear(String conversationId) {
			this.cleared.add(conversationId);
		}

	}

}
