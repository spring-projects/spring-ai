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

package org.springframework.ai.chat.messages;

import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.chat.messages.AbstractMessage.MESSAGE_TYPE;

/**
 * Unit tests for {@link UserMessage}.
 *
 * @author Thomas Vitale
 */
class UserMessageTests {

	@Test
	void userMessageWithNullText() {
		assertThatThrownBy(() -> new UserMessage((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");
		;
	}

	@Test
	void userMessageWithTextContent() {
		String text = "Hello, world!";
		UserMessage message = new UserMessage(text);
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageWithNullResource() {
		assertThatThrownBy(() -> new UserMessage((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resource cannot be null");
		;
	}

	@Test
	void userMessageWithResource() {
		UserMessage message = new UserMessage(new ClassPathResource("prompt-user.txt"));
		assertThat(message.getText()).isEqualTo("Hello, world!");
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageFromBuilderWithText() {
		String text = "Hello, world!";
		UserMessage message = UserMessage.builder()
			.text(text)
			.media(new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt")))
			.metadata(Map.of("key", "value"))
			.build();
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).hasSize(1);
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key", "value");
	}

	@Test
	void userMessageFromBuilderWithResource() {
		UserMessage message = UserMessage.builder().text(new ClassPathResource("prompt-user.txt")).build();
		assertThat(message.getText()).isEqualTo("Hello, world!");
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageCopy() {
		String text1 = "Hello, world!";
		Media media1 = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata1 = Map.of("key", "value");
		UserMessage userMessage1 = UserMessage.builder().text(text1).media(media1).metadata(metadata1).build();

		UserMessage userMessage2 = userMessage1.copy();

		assertThat(userMessage2.getText()).isEqualTo(text1);
		assertThat(userMessage2.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

	@Test
	void userMessageMutate() {
		String text1 = "Hello, world!";
		Media media1 = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata1 = Map.of("key", "value");
		UserMessage userMessage1 = UserMessage.builder().text(text1).media(media1).metadata(metadata1).build();

		UserMessage userMessage2 = userMessage1.mutate().build();

		assertThat(userMessage2.getText()).isEqualTo(text1);
		assertThat(userMessage2.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);

		String text3 = "Farewell, Aragog!";
		UserMessage userMessage3 = userMessage2.mutate().text(text3).build();

		assertThat(userMessage3.getText()).isEqualTo(text3);
		assertThat(userMessage3.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage3.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

}
