/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.deepseek;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link DeepSeekAssistantMessage}.
 *
 * @author Sun Yuhan
 */
class DeepSeekAssistantMessageTests {

	@Test
	public void testConstructorWithContentOnly() {
		String content = "Hello, world!";
		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content(content).build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isNull();
		assertThat(message.getPrefix()).isNull();
	}

	@Test
	public void testConstructorWithContentAndReasoningContent() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content(content)
			.reasoningContent(reasoningContent)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
		assertThat(message.getPrefix()).isNull();
	}

	@Test
	public void testConstructorWithContentAndProperties() {
		String content = "Hello, world!";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		properties.put("key2", 123);

		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content(content)
			.properties(properties)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getReasoningContent()).isNull();
		assertThat(message.getPrefix()).isNull();
	}

	@Test
	public void testConstructorWithContentPropertiesAndToolCalls() {
		String content = "Hello, world!";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");

		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));

		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content(content)
			.properties(properties)
			.toolCalls(toolCalls)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
		assertThat(message.getReasoningContent()).isNull();
		assertThat(message.getPrefix()).isNull();
	}

	@Test
	public void testConstructorWithAllParameters() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		Boolean prefix = true;
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));

		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content(content)
			.reasoningContent(reasoningContent)
			.properties(properties)
			.toolCalls(toolCalls)
			.prefix(prefix)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
		assertThat(message.getPrefix()).isEqualTo(prefix);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
	}

	@Test
	public void testPrefixAssistantMessageFactoryMethod() {
		String content = "Hello, world!";
		DeepSeekAssistantMessage message = DeepSeekAssistantMessage.prefixAssistantMessage(content);

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isNull();
	}

	@Test
	public void testPrefixAssistantMessageFactoryMethodWithReasoning() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		DeepSeekAssistantMessage message = DeepSeekAssistantMessage.prefixAssistantMessage(content, reasoningContent);

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
	}

	@Test
	public void testSettersAndGetters() {
		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().build();

		String reasoningContent = "New reasoning content";
		Boolean prefix = false;

		message.setReasoningContent(reasoningContent);
		message.setPrefix(prefix);

		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
		assertThat(message.getPrefix()).isEqualTo(prefix);
	}

	@Test
	public void testEqualsAndHashCode() {
		DeepSeekAssistantMessage message1 = new DeepSeekAssistantMessage("content", "reasoning", true, Map.of(),
				List.of(), List.of(), false);
		DeepSeekAssistantMessage message2 = new DeepSeekAssistantMessage("content", "reasoning", true, Map.of(),
				List.of(), List.of(), false);

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());

		DeepSeekAssistantMessage message3 = new DeepSeekAssistantMessage("content", "different reasoning", true,
				Map.of(), List.of(), List.of(), false);
		assertThat(message1).isNotEqualTo(message3);
	}

	@Test
	public void testToString() {
		DeepSeekAssistantMessage message = new DeepSeekAssistantMessage.Builder().content("content")
			.reasoningContent("reasoning")
			.build();
		message.setPrefix(true);

		assertThatNoException().isThrownBy(message::toString);
		assertThat(message.toString()).contains("content", "reasoning", "true");
	}

	@Test
	public void testBuilderComplete() {
		Map<String, Object> properties = Map.of("key", "value");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "testFunction", "{}"));
		List<Media> media = List.of();

		DeepSeekAssistantMessage.Builder builder = new DeepSeekAssistantMessage.Builder();
		DeepSeekAssistantMessage message = builder.content("content")
			.reasoningContent("reasoning")
			.prefix(true)
			.properties(properties)
			.toolCalls(toolCalls)
			.media(media)
			.build();

		assertThat(message.getText()).isEqualTo("content");
		assertThat(message.getReasoningContent()).isEqualTo("reasoning");
		assertThat(message.getPrefix()).isEqualTo(true);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
		assertThat(message.getMedia()).isEqualTo(media);
	}

}
