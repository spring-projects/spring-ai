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

package org.springframework.ai.session;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageFilter} factories and composition.
 */
class MessageFilterTests {

	// --- all() ---

	@Test
	void allAcceptsEveryMessageType() {
		MessageFilter filter = MessageFilter.all();

		assertThat(filter.shouldPersist(new UserMessage("hi"))).isTrue();
		assertThat(filter.shouldPersist(new AssistantMessage("hello"))).isTrue();
		assertThat(filter.shouldPersist(new SystemMessage("be nice"))).isTrue();
		assertThat(filter.shouldPersist(new AssistantMessage(""))).isTrue();
	}

	// --- skipEmptyMessages() ---

	@Test
	void skipEmptyAssistantMessagesRejectsBlankTextAssistantMessage() {
		MessageFilter filter = MessageFilter.skipEmptyMessages();

		assertThat(filter.shouldPersist(new AssistantMessage(""))).isFalse();
		assertThat(filter.shouldPersist(new AssistantMessage("   "))).isFalse();
	}

	@Test
	void skipEmptyAssistantMessagesRejectsNullTextAssistantMessage() {
		MessageFilter filter = MessageFilter.skipEmptyMessages();

		assertThat(filter.shouldPersist(AssistantMessage.builder().build())).isFalse();
	}

	@Test
	void skipEmptyAssistantMessagesAcceptsAssistantMessageWithText() {
		MessageFilter filter = MessageFilter.skipEmptyMessages();

		assertThat(filter.shouldPersist(new AssistantMessage("real answer"))).isTrue();
	}

	@Test
	void skipEmptyAssistantMessagesAcceptsAssistantMessageWithToolCallsOnly() {
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{}")))
			.build();

		assertThat(MessageFilter.skipEmptyMessages().shouldPersist(withToolCalls)).isTrue();
	}

	@Test
	void skipEmptyAssistantMessagesAcceptsAssistantMessageWithMediaOnly() {
		Media image = new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png"));
		AssistantMessage withMedia = AssistantMessage.builder().media(List.of(image)).build();

		assertThat(MessageFilter.skipEmptyMessages().shouldPersist(withMedia)).isTrue();
	}

	@Test
	void skipEmptyAssistantMessagesAcceptsNonAssistantMessagesRegardlessOfContent() {
		// Only AssistantMessages are subject to the emptiness check — a blank user
		// message must pass.
		MessageFilter filter = MessageFilter.skipEmptyMessages();

		assertThat(filter.shouldPersist(new UserMessage(""))).isTrue();
		assertThat(filter.shouldPersist(new SystemMessage(""))).isTrue();
	}

	// --- byMessageType() ---

	@Test
	void byMessageTypeAcceptsListedTypesAndRejectsOthers() {
		MessageFilter filter = MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT);

		assertThat(filter.shouldPersist(new UserMessage("hi"))).isTrue();
		assertThat(filter.shouldPersist(new AssistantMessage("hello"))).isTrue();
		assertThat(filter.shouldPersist(new SystemMessage("be nice"))).isFalse();
	}

	@Test
	void byMessageTypeRejectsEmptyVarargs() {
		assertThatThrownBy(MessageFilter::byMessageType).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("types must not be empty");
	}

	// --- containsText() ---

	@Test
	void containsTextMatchesCaseInsensitively() {
		MessageFilter filter = MessageFilter.containsText("SpRiNg");

		assertThat(filter.shouldPersist(new UserMessage("I love Spring AI"))).isTrue();
		assertThat(filter.shouldPersist(new UserMessage("I love Quarkus"))).isFalse();
	}

	@Test
	void containsTextRejectsNullTextMessage() {
		MessageFilter filter = MessageFilter.containsText("spring");

		assertThat(filter.shouldPersist(AssistantMessage.builder().build())).isFalse();
	}

	@Test
	void containsTextRejectsBlankKeyword() {
		assertThatThrownBy(() -> MessageFilter.containsText("  ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("keyword must not be blank");
	}

	// --- Composition ---

	@Test
	void andRequiresBothFiltersToAccept() {
		MessageFilter filter = MessageFilter.byMessageType(MessageType.ASSISTANT)
			.and(MessageFilter.skipEmptyMessages());

		assertThat(filter.shouldPersist(new AssistantMessage("answer"))).isTrue();
		assertThat(filter.shouldPersist(new AssistantMessage(""))).isFalse();
		assertThat(filter.shouldPersist(new UserMessage("question"))).isFalse();
	}

	@Test
	void orAcceptsWhenEitherFilterAccepts() {
		MessageFilter filter = MessageFilter.byMessageType(MessageType.USER)
			.or(MessageFilter.byMessageType(MessageType.SYSTEM));

		assertThat(filter.shouldPersist(new UserMessage("hi"))).isTrue();
		assertThat(filter.shouldPersist(new SystemMessage("be nice"))).isTrue();
		assertThat(filter.shouldPersist(new AssistantMessage("hello"))).isFalse();
	}

	@Test
	void negateInvertsDecision() {
		MessageFilter filter = MessageFilter.containsText("secret").negate();

		assertThat(filter.shouldPersist(new UserMessage("this is a secret token"))).isFalse();
		assertThat(filter.shouldPersist(new UserMessage("nothing to hide"))).isTrue();
	}

	@Test
	void andRejectsNullArgument() {
		assertThatThrownBy(() -> MessageFilter.all().and(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("other must not be null");
	}

	@Test
	void orRejectsNullArgument() {
		assertThatThrownBy(() -> MessageFilter.all().or(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("other must not be null");
	}

}
