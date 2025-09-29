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

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.ai.chat.messages.AbstractMessage.MESSAGE_TYPE;

/**
 * Unit tests for {@link SystemMessage}.
 *
 * @author Thomas Vitale
 */
class SystemMessageTests {

	@Test
	void systemMessageWithNullText() {
		assertThrows(IllegalArgumentException.class, () -> new SystemMessage((String) null));
	}

	@Test
	void systemMessageWithTextContent() {
		String text = "Tell me, did you sail across the sun?";
		SystemMessage message = new SystemMessage(text);
		assertEquals(text, message.getText());
		assertEquals(MessageType.SYSTEM, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void systemMessageWithNullResource() {
		assertThrows(IllegalArgumentException.class, () -> new SystemMessage((Resource) null));
	}

	@Test
	void systemMessageWithResource() {
		SystemMessage message = new SystemMessage(new ClassPathResource("prompt-system.txt"));
		assertEquals("Tell me, did you sail across the sun?", message.getText());
		assertEquals(MessageType.SYSTEM, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void systemMessageFromBuilderWithText() {
		String text = "Tell me, did you sail across the sun?";
		SystemMessage message = SystemMessage.builder().text(text).metadata(Map.of("key", "value")).build();
		assertEquals(text, message.getText());
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.SYSTEM)
			.containsEntry("key", "value");
	}

	@Test
	void systemMessageFromBuilderWithResource() {
		Resource resource = new ClassPathResource("prompt-system.txt");
		SystemMessage message = SystemMessage.builder().text(resource).metadata(Map.of("key", "value")).build();
		assertEquals("Tell me, did you sail across the sun?", message.getText());
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.SYSTEM)
			.containsEntry("key", "value");
	}

	@Test
	void systemMessageCopy() {
		String text1 = "Tell me, did you sail across the sun?";
		Map<String, Object> metadata1 = Map.of("key", "value");
		SystemMessage systemMessage1 = SystemMessage.builder().text(text1).metadata(metadata1).build();

		SystemMessage systemMessage2 = systemMessage1.copy();

		assertThat(systemMessage2.getText()).isEqualTo(text1);
		assertThat(systemMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

	@Test
	void systemMessageMutate() {
		String text1 = "Tell me, did you sail across the sun?";
		Map<String, Object> metadata1 = Map.of("key", "value");
		SystemMessage systemMessage1 = SystemMessage.builder().text(text1).metadata(metadata1).build();

		SystemMessage systemMessage2 = systemMessage1.mutate().build();

		assertThat(systemMessage2.getText()).isEqualTo(text1);
		assertThat(systemMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);

		String text3 = "Farewell, Aragog!";
		SystemMessage systemMessage3 = systemMessage2.mutate().text(text3).build();

		assertThat(systemMessage3.getText()).isEqualTo(text3);
		assertThat(systemMessage3.getMetadata()).hasSize(2).isNotSameAs(systemMessage2.getMetadata());
	}

	@Test
	void systemMessageWithEmptyText() {
		SystemMessage message = new SystemMessage("");
		assertEquals("", message.getText());
		assertEquals(MessageType.SYSTEM, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void systemMessageWithWhitespaceText() {
		String text = "   \t\n   ";
		SystemMessage message = new SystemMessage(text);
		assertEquals(text, message.getText());
		assertEquals(MessageType.SYSTEM, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void systemMessageBuilderWithNullText() {
		assertThrows(IllegalArgumentException.class, () -> SystemMessage.builder().text((String) null).build());
	}

	@Test
	void systemMessageBuilderWithNullResource() {
		assertThrows(IllegalArgumentException.class, () -> SystemMessage.builder().text((Resource) null).build());
	}

	@Test
	void systemMessageBuilderWithEmptyMetadata() {
		String text = "Test message";
		SystemMessage message = SystemMessage.builder().text(text).metadata(Map.of()).build();
		assertEquals(text, message.getText());
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.SYSTEM);
	}

	@Test
	void systemMessageBuilderOverwriteMetadata() {
		String text = "Test message";
		SystemMessage message = SystemMessage.builder()
			.text(text)
			.metadata(Map.of("key1", "value1"))
			.metadata(Map.of("key2", "value2"))
			.build();

		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.SYSTEM)
			.containsEntry("key2", "value2")
			.doesNotContainKey("key1");
	}

	@Test
	void systemMessageCopyPreservesImmutability() {
		String text = "Original text";
		Map<String, Object> originalMetadata = Map.of("key", "value");
		SystemMessage original = SystemMessage.builder().text(text).metadata(originalMetadata).build();

		SystemMessage copy = original.copy();

		// Verify they are different instances
		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getMetadata()).isNotSameAs(original.getMetadata());

		// Verify content is equal
		assertThat(copy.getText()).isEqualTo(original.getText());
		assertThat(copy.getMetadata()).isEqualTo(original.getMetadata());
	}

	@Test
	void systemMessageMutateWithNewMetadata() {
		String originalText = "Original text";
		SystemMessage original = SystemMessage.builder().text(originalText).metadata(Map.of("key1", "value1")).build();

		SystemMessage mutated = original.mutate().metadata(Map.of("key2", "value2")).build();

		assertThat(mutated.getText()).isEqualTo(originalText);
		assertThat(mutated.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.SYSTEM)
			.containsEntry("key2", "value2")
			.doesNotContainKey("key1");
	}

	@Test
	void systemMessageMutateChaining() {
		SystemMessage original = SystemMessage.builder().text("Original").metadata(Map.of("key1", "value1")).build();

		SystemMessage result = original.mutate().text("Updated").metadata(Map.of("key2", "value2")).build();

		assertThat(result.getText()).isEqualTo("Updated");
		assertThat(result.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.SYSTEM)
			.containsEntry("key2", "value2");
	}

	@Test
	void systemMessageEqualsAndHashCode() {
		String text = "Test message";
		Map<String, Object> metadata = Map.of("key", "value");

		SystemMessage message1 = SystemMessage.builder().text(text).metadata(metadata).build();

		SystemMessage message2 = SystemMessage.builder().text(text).metadata(metadata).build();

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
	}

	@Test
	void systemMessageNotEqualsWithDifferentText() {
		SystemMessage message1 = new SystemMessage("Text 1");
		SystemMessage message2 = new SystemMessage("Text 2");

		assertThat(message1).isNotEqualTo(message2);
	}

}
