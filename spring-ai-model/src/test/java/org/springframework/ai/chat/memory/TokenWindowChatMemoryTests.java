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
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TokenWindowChatMemory}.
 *
 * @author JiaWeiDong
 */
public class TokenWindowChatMemoryTests {

	/**
	 * Simple token estimator that counts the number of characters in the message text.
	 * This makes test assertions easy to reason about: "Hello" = 5 tokens.
	 */
	private static final TokenCountEstimator CHAR_COUNT_ESTIMATOR = new TokenCountEstimator() {
		@Override
		public int estimate(String text) {
			return text == null ? 0 : text.length();
		}

		@Override
		public int estimate(MediaContent content) {
			return estimate(content.getText());
		}

		@Override
		public int estimate(Iterable<MediaContent> contents) {
			int total = 0;
			for (MediaContent content : contents) {
				total += estimate(content);
			}
			return total;
		}
	};

	private final TokenWindowChatMemory chatMemory = TokenWindowChatMemory.builder()
		.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
		.maxTokenSize(50)
		.build();

	@Test
	void zeroMaxTokenSizeNotAllowed() {
		assertThatThrownBy(
				() -> TokenWindowChatMemory.builder().tokenCountEstimator(CHAR_COUNT_ESTIMATOR).maxTokenSize(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxTokenSize must be greater than 0");
	}

	@Test
	void negativeMaxTokenSizeNotAllowed() {
		assertThatThrownBy(() -> TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(-1)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("maxTokenSize must be greater than 0");
	}

	@Test
	void tokenCountEstimatorRequired() {
		assertThatThrownBy(() -> TokenWindowChatMemory.builder().maxTokenSize(100).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("tokenCountEstimator must be set");
	}

	@Test
	void handleMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there"));

		this.chatMemory.add(conversationId, messages);

		assertThat(this.chatMemory.get(conversationId)).containsAll(messages);

		this.chatMemory.clear(conversationId);

		assertThat(this.chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void handleSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");

		this.chatMemory.add(conversationId, message);

		assertThat(this.chatMemory.get(conversationId)).contains(message);

		this.chatMemory.clear(conversationId);

		assertThat(this.chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemory.add(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.add(null, new UserMessage("Hello")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.get(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.clear(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemory.add("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.get("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.clear("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.chatMemory.add(conversationId, (List<Message>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void nullMessageNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.chatMemory.add(conversationId, (Message) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("message cannot be null");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> this.chatMemory.add(conversationId, messagesWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

	@Test
	void noEvictionWhenTokensWithinLimit() {
		// "Hello" (5) + "Hi" (2) + "How?" (4) = 11 tokens, well within 50 limit
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(50)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId, List.of(new UserMessage("Hello"), new AssistantMessage("Hi")));
		memory.add(conversationId, List.of(new UserMessage("How?")));

		List<Message> result = memory.get(conversationId);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new UserMessage("Hello"), new AssistantMessage("Hi"),
				new UserMessage("How?"));
	}

	@Test
	void evictionWhenTokensExceedLimit() {
		// maxTokenSize = 20
		// "Message one" (11) + "Response one" (12) = 23 > 20 after second add
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(20)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId, List.of(new UserMessage("Message one"), new AssistantMessage("Response one")));

		// Add more: "Q2" (2) + "A2" (2) = 4, total would be 23+4=27 > 20
		memory.add(conversationId, List.of(new UserMessage("Q2"), new AssistantMessage("A2")));

		List<Message> result = memory.get(conversationId);

		// After eviction: "Response one" (12) would be orphaned, so turn-boundary
		// snapping evicts it too. Remaining: "Q2" (2) + "A2" (2) = 4 <= 20
		assertThat(result).containsExactly(new UserMessage("Q2"), new AssistantMessage("A2"));
	}

	@Test
	void systemMessageIsPreservedDuringEviction() {
		// maxTokenSize = 30
		// "System" (6) + "Msg1" (4) + "Resp1" (5) + "Msg2" (4) + "Resp2" (5) = 24
		// Then add "Msg3" (4) + "Resp3" (5) = 9, total = 33 > 30
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(30)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId, List.of(new SystemMessage("System"), new UserMessage("Msg1"),
				new AssistantMessage("Resp1"), new UserMessage("Msg2"), new AssistantMessage("Resp2")));
		memory.add(conversationId, List.of(new UserMessage("Msg3"), new AssistantMessage("Resp3")));

		List<Message> result = memory.get(conversationId);

		// SystemMessage preserved. "Msg1"(4)+"Resp1"(5)=9 evicted to get under 30.
		// Remaining: "System"(6)+"Msg2"(4)+"Resp2"(5)+"Msg3"(4)+"Resp3"(5)=24 <= 30
		assertThat(result).containsExactly(new SystemMessage("System"), new UserMessage("Msg2"),
				new AssistantMessage("Resp2"), new UserMessage("Msg3"), new AssistantMessage("Resp3"));
	}

	@Test
	void turnBoundarySnappingPreventsOrphanedAssistantMessage() {
		// maxTokenSize = 15
		// "Q1"(2) + "A1 is long"(10) + "Q2"(2) + "A2"(2) = 16 > 15
		// Evicting "Q1"(2) -> 14 <= 15, but "A1 is long" is ASSISTANT -> orphaned
		// Snap forward to "Q2", evict "A1 is long" too -> "Q2"(2)+"A2"(2) = 4
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(15)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId, List.of(new UserMessage("Q1"), new AssistantMessage("A1 is long"),
				new UserMessage("Q2"), new AssistantMessage("A2")));

		List<Message> result = memory.get(conversationId);

		assertThat(result).containsExactly(new UserMessage("Q2"), new AssistantMessage("A2"));
	}

	@Test
	void longMessageEvictedBeforeShortMessages() {
		// This is the key advantage over MessageWindowChatMemory:
		// One long message should consume more "budget" than several short ones.
		// maxTokenSize = 20
		// "Short"(5) + "A very long response that takes many tokens"(44) = 49 > 20
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(20)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId,
				List.of(new UserMessage("Short"), new AssistantMessage("A very long response that takes many tokens")));
		// Add new turn: "Hi"(2) + "Ok"(2) = 4
		memory.add(conversationId, List.of(new UserMessage("Hi"), new AssistantMessage("Ok")));

		List<Message> result = memory.get(conversationId);

		// Old turn evicted (token-heavy), new turn kept
		assertThat(result).containsExactly(new UserMessage("Hi"), new AssistantMessage("Ok"));
	}

	@Test
	void oldSystemMessagesAreRemovedWhenNewOneAdded() {
		TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(CHAR_COUNT_ESTIMATOR)
			.maxTokenSize(50)
			.build();

		String conversationId = UUID.randomUUID().toString();
		memory.add(conversationId, List.of(new SystemMessage("Old system 1"), new SystemMessage("Old system 2")));

		memory.add(conversationId, List.of(new SystemMessage("New system")));

		List<Message> result = memory.get(conversationId);

		assertThat(result).hasSize(1);
		assertThat(result).containsExactly(new SystemMessage("New system"));
	}

	@Test
	void emptyMessageList() {
		String conversationId = UUID.randomUUID().toString();

		List<Message> result = this.chatMemory.get(conversationId);

		assertThat(result).isEmpty();
	}

}
