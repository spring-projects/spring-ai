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

package org.springframework.ai.mistralai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link MistralAiAssistantMessage}. Tests the builder pattern,
 * equals/hashCode contract, and proper handling of the thinkingContent field for
 * Magistral reasoning models.
 *
 * @author Kyle Kreuter
 */
class MistralAiAssistantMessageTests {

	// Builder Tests

	@Test
	void testBuildMessageWithContentOnly() {
		String content = "Hello, world!";
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content(content).build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getThinkingContent()).isNull();
		assertThat(message.getToolCalls()).isEmpty();
		assertThat(message.getMedia()).isEmpty();
	}

	@Test
	void testBuildMessageWithContentAndThinkingContent() {
		String content = "The answer is 42.";
		String thinkingContent = "Let me calculate this step by step...";

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content(content)
			.thinkingContent(thinkingContent)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getThinkingContent()).isEqualTo(thinkingContent);
	}

	@Test
	void testBuildMessageWithAllProperties() {
		String content = "Response content";
		String thinkingContent = "Thinking process";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		properties.put("key2", 123);

		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "testFunction", "{}"));

		List<Media> media = List.of();

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content(content)
			.thinkingContent(thinkingContent)
			.properties(properties)
			.toolCalls(toolCalls)
			.media(media)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getThinkingContent()).isEqualTo(thinkingContent);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
		assertThat(message.getMedia()).isEqualTo(media);
	}

	@Test
	void testBuildMessageWithNullContent() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content(null).build();

		assertThat(message.getText()).isNull();
		assertThat(message.getThinkingContent()).isNull();
	}

	@Test
	void testBuildMessageWithEmptyContent() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("").build();

		assertThat(message.getText()).isEmpty();
	}

	@Test
	void testDefaultEmptyCollectionsWhenNotSpecified() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		assertThat(message.getToolCalls()).isNotNull().isEmpty();
		assertThat(message.getMedia()).isNotNull().isEmpty();
		assertThat(message.getMetadata()).isNotNull();
	}

	// Setter Tests

	@Test
	void testSetAndGetThinkingContent() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		String thinkingContent = "New thinking content";
		message.setThinkingContent(thinkingContent);

		assertThat(message.getThinkingContent()).isEqualTo(thinkingContent);
	}

	@Test
	void testMethodChainingOnSetThinkingContent() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		MistralAiAssistantMessage result = message.setThinkingContent("thinking");

		assertThat(result).isSameAs(message);
		assertThat(message.getThinkingContent()).isEqualTo("thinking");
	}

	@Test
	void testSettingThinkingContentToNull() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("initial thinking")
			.build();

		message.setThinkingContent(null);

		assertThat(message.getThinkingContent()).isNull();
	}

	// Equals and HashCode Tests

	@Test
	void testEqualityForSameContentAndThinkingContent() {
		MistralAiAssistantMessage message1 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking")
			.build();

		MistralAiAssistantMessage message2 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking")
			.build();

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
	}

	@Test
	void testInequalityForDifferentThinkingContent() {
		MistralAiAssistantMessage message1 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking1")
			.build();

		MistralAiAssistantMessage message2 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking2")
			.build();

		assertThat(message1).isNotEqualTo(message2);
	}

	@Test
	void testInequalityForDifferentContent() {
		MistralAiAssistantMessage message1 = new MistralAiAssistantMessage.Builder().content("content1")
			.thinkingContent("thinking")
			.build();

		MistralAiAssistantMessage message2 = new MistralAiAssistantMessage.Builder().content("content2")
			.thinkingContent("thinking")
			.build();

		assertThat(message1).isNotEqualTo(message2);
	}

	@Test
	void testNullThinkingContentEquality() {
		MistralAiAssistantMessage message1 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent(null)
			.build();

		MistralAiAssistantMessage message2 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent(null)
			.build();

		MistralAiAssistantMessage message3 = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking")
			.build();

		assertThat(message1).isEqualTo(message2);
		assertThat(message1).isNotEqualTo(message3);
	}

	@Test
	void testEqualityToItself() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking")
			.build();

		assertThat(message).isEqualTo(message);
	}

	@Test
	void testInequalityToNull() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		assertThat(message).isNotEqualTo(null);
	}

	@Test
	void testInequalityToDifferentType() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		assertThat(message).isNotEqualTo("content");
	}

	// ToString Tests

	@Test
	void testToStringDoesNotThrowException() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.thinkingContent("thinking")
			.build();

		assertThatNoException().isThrownBy(message::toString);
	}

	@Test
	void testToStringIncludesRelevantFields() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("test content")
			.thinkingContent("test thinking")
			.build();

		String toString = message.toString();

		assertThat(toString).contains("test content");
		assertThat(toString).contains("test thinking");
		assertThat(toString).contains("MistralAiAssistantMessage");
	}

	@Test
	void testToStringHandlesNullThinkingContent() {
		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content").build();

		assertThatNoException().isThrownBy(message::toString);
		assertThat(message.toString()).contains("null");
	}

	// Tool Calls Tests

	@Test
	void testBuildMessageWithToolCalls() {
		List<ToolCall> toolCalls = List.of(new ToolCall("call-1", "function", "getWeather", "{\"city\":\"Paris\"}"),
				new ToolCall("call-2", "function", "getTime", "{\"timezone\":\"UTC\"}"));

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.toolCalls(toolCalls)
			.build();

		assertThat(message.getToolCalls()).hasSize(2);
		assertThat(message.getToolCalls().get(0).id()).isEqualTo("call-1");
		assertThat(message.getToolCalls().get(0).name()).isEqualTo("getWeather");
		assertThat(message.getToolCalls().get(1).id()).isEqualTo("call-2");
		assertThat(message.getToolCalls().get(1).name()).isEqualTo("getTime");
	}

	@Test
	void testBuildMessageWithThinkingContentAndToolCalls() {
		List<ToolCall> toolCalls = List.of(new ToolCall("call-1", "function", "calculator", "{\"op\":\"add\"}"));

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("Let me calculate...")
			.thinkingContent("I need to use the calculator tool")
			.toolCalls(toolCalls)
			.build();

		assertThat(message.getText()).isEqualTo("Let me calculate...");
		assertThat(message.getThinkingContent()).isEqualTo("I need to use the calculator tool");
		assertThat(message.getToolCalls()).hasSize(1);
	}

	// Properties/Metadata Tests

	@Test
	void testBuildMessageWithProperties() {
		Map<String, Object> properties = Map.of("id", "msg-123", "role", "assistant", "finishReason", "stop");

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.properties(properties)
			.build();

		assertThat(message.getMetadata()).containsEntry("id", "msg-123")
			.containsEntry("role", "assistant")
			.containsEntry("finishReason", "stop");
	}

	@Test
	void testMutablePropertiesMapInBuilder() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("key", "value");

		MistralAiAssistantMessage message = new MistralAiAssistantMessage.Builder().content("content")
			.properties(properties)
			.build();

		// Modifying original map should not affect the message
		properties.put("newKey", "newValue");

		assertThat(message.getMetadata()).containsKey("key");
	}

}
