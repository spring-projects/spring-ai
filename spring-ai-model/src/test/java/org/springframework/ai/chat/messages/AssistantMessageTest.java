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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AssistantMessage} with name property support.
 *
 * @author Spring AI Team
 */
class AssistantMessageTest {

	@Test
	void shouldCreateAssistantMessageWithName() {
		AssistantMessage message = new AssistantMessage("Hello", "Alice");
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isEqualTo("Alice");
		assertThat(message.getMessageType()).isEqualTo(MessageType.ASSISTANT);
	}

	@Test
	void shouldCreateAssistantMessageWithNameAndProperties() {
		Map<String, Object> properties = Map.of("key", "value");
		AssistantMessage message = new AssistantMessage("Hello", properties, "Bob");
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isEqualTo("Bob");
		assertThat(message.getMetadata()).containsEntry("key", "value");
	}

	@Test
	void shouldCreateAssistantMessageWithNameAndToolCalls() {
		List<AssistantMessage.ToolCall> toolCalls = List
			.of(new AssistantMessage.ToolCall("1", "function", "testTool", "{}"));
		AssistantMessage message = new AssistantMessage("Hello", Map.of(), toolCalls, "Charlie");
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isEqualTo("Charlie");
		assertThat(message.getToolCalls()).hasSize(1);
		assertThat(message.getToolCalls().get(0).name()).isEqualTo("testTool");
	}

	@Test
	void shouldCreateAssistantMessageWithNameAndMedia() {
		List<AssistantMessage.ToolCall> toolCalls = List.of();
		List<Media> media = List.of();
		AssistantMessage message = new AssistantMessage("Hello", Map.of(), toolCalls, media, "David");
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isEqualTo("David");
		assertThat(message.getToolCalls()).isEmpty();
		assertThat(message.getMedia()).isEmpty();
	}

	@Test
	void shouldHandleNullName() {
		AssistantMessage message = new AssistantMessage("Hello", Map.of(), List.of(), List.of(), null);
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isNull();
	}

	@Test
	void shouldHandleEmptyName() {
		AssistantMessage message = new AssistantMessage("Hello", "");
		assertThat(message.getText()).isEqualTo("Hello");
		assertThat(message.getName()).isEqualTo("");
	}

	@Test
	void shouldBeEqualWithSameName() {
		AssistantMessage message1 = new AssistantMessage("Hello", "Alice");
		AssistantMessage message2 = new AssistantMessage("Hello", "Alice");
		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
	}

	@Test
	void shouldNotBeEqualWithDifferentName() {
		AssistantMessage message1 = new AssistantMessage("Hello", "Alice");
		AssistantMessage message2 = new AssistantMessage("Hello", "Bob");
		assertThat(message1).isNotEqualTo(message2);
	}

	@Test
	void shouldIncludeNameInToString() {
		AssistantMessage message = new AssistantMessage("Hello", "Alice");
		String toString = message.toString();
		assertThat(toString).contains("name=Alice");
	}

	@Test
	void shouldHandleNullNameInToString() {
		AssistantMessage message = new AssistantMessage("Hello", Map.of(), List.of(), List.of(), null);
		String toString = message.toString();
		assertThat(toString).contains("name=null");
	}

}