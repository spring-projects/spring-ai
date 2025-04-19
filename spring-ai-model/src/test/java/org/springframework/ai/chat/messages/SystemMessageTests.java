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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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

}
