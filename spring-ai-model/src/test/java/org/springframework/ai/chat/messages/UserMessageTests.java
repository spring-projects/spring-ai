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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

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

	@Test
	void userMessageWithEmptyText() {
		UserMessage message = new UserMessage("");
		assertThat(message.getText()).isEmpty();
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageWithWhitespaceText() {
		String text = "   \t\n   ";
		UserMessage message = new UserMessage(text);
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderWithNullText() {
		assertThatThrownBy(() -> UserMessage.builder().text((String) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");
	}

	@Test
	void userMessageBuilderWithEmptyMediaList() {
		String text = "No media attached";
		UserMessage message = UserMessage.builder().text(text).build();

		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderWithEmptyMetadata() {
		String text = "Test message";
		UserMessage message = UserMessage.builder().text(text).metadata(Map.of()).build();

		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderOverwriteMetadata() {
		String text = "Test message";
		UserMessage message = UserMessage.builder()
			.text(text)
			.metadata(Map.of("key1", "value1"))
			.metadata(Map.of("key2", "value2"))
			.build();

		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key2", "value2")
			.doesNotContainKey("key1");
	}

	@Test
	void userMessageCopyWithNoMedia() {
		String text = "Simple message";
		Map<String, Object> metadata = Map.of("key", "value");
		UserMessage original = UserMessage.builder().text(text).metadata(metadata).build();

		UserMessage copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getText()).isEqualTo(text);
		assertThat(copy.getMedia()).isEmpty();
		assertThat(copy.getMetadata()).isNotSameAs(original.getMetadata()).isEqualTo(original.getMetadata());
	}

	@Test
	void userMessageMutateAddMedia() {
		String text = "Original message";
		UserMessage original = UserMessage.builder().text(text).build();

		Media newMedia = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage mutated = original.mutate().media(newMedia).build();

		assertThat(original.getMedia()).isEmpty();
		assertThat(mutated.getMedia()).hasSize(1).contains(newMedia);
		assertThat(mutated.getText()).isEqualTo(text);
	}

	@Test
	void userMessageMutateChaining() {
		UserMessage original = UserMessage.builder().text("Original").build();

		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage result = original.mutate().text("Updated").media(media).metadata(Map.of("key", "value")).build();

		assertThat(result.getText()).isEqualTo("Updated");
		assertThat(result.getMedia()).hasSize(1).contains(media);
		assertThat(result.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key", "value");
	}

	@Test
	void userMessageEqualsAndHashCode() {
		String text = "Test message";
		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata = Map.of("key", "value");

		UserMessage message1 = UserMessage.builder().text(text).media(media).metadata(metadata).build();

		UserMessage message2 = UserMessage.builder().text(text).media(media).metadata(metadata).build();

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
	}

	@Test
	void userMessageNotEqualsWithDifferentText() {
		UserMessage message1 = new UserMessage("Text 1");
		UserMessage message2 = new UserMessage("Text 2");

		assertThat(message1).isNotEqualTo(message2);
	}

	@Test
	void userMessageToString() {
		String text = "Test message";
		UserMessage message = new UserMessage(text);

		String toString = message.toString();
		assertThat(toString).contains("UserMessage").contains(text).contains("USER");
	}

	@Test
	void userMessageToStringWithMedia() {
		String text = "Test with media";
		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage message = UserMessage.builder().text(text).media(media).build();

		String toString = message.toString();
		assertThat(toString).contains("UserMessage").contains(text).contains("media");
	}

}
