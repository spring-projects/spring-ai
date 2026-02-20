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

package org.springframework.ai.zhipuai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link ZhiPuAiAssistantMessage}.
 *
 * @author Sun Yuhan
 */
class ZhiPuAiAssistantMessageTests {

	@Test
	public void testConstructorWithContentOnly() {
		String content = "Hello, world!";
		ZhiPuAiAssistantMessage message = new ZhiPuAiAssistantMessage.Builder().content(content).build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isNull();
	}

	@Test
	public void testConstructorWithAllParameters() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));
		List<Media> media = List.of();

		ZhiPuAiAssistantMessage message = new ZhiPuAiAssistantMessage.Builder().content(content)
			.reasoningContent(reasoningContent)
			.properties(properties)
			.toolCalls(toolCalls)
			.media(media)
			.build();

		assertThat(message.getText()).isEqualTo(content);
		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
	}

	@Test
	public void testSettersAndGetters() {
		ZhiPuAiAssistantMessage message = new ZhiPuAiAssistantMessage.Builder().content("test").build();

		String reasoningContent = "New reasoning content";

		message.setReasoningContent(reasoningContent);

		assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
	}

	@Test
	public void testEqualsAndHashCode() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));
		List<Media> media = List.of();

		ZhiPuAiAssistantMessage message1 = new ZhiPuAiAssistantMessage(content, reasoningContent, properties, toolCalls,
				media, false);
		ZhiPuAiAssistantMessage message2 = new ZhiPuAiAssistantMessage(content, reasoningContent, properties, toolCalls,
				media, false);

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());

		ZhiPuAiAssistantMessage message3 = new ZhiPuAiAssistantMessage(content, "different reasoning", properties,
				toolCalls, media, false);
		assertThat(message1).isNotEqualTo(message3);
	}

	@Test
	public void testToString() {
		String content = "Hello, world!";
		String reasoningContent = "This is my reasoning";
		Map<String, Object> properties = new HashMap<>();
		properties.put("key1", "value1");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));
		List<Media> media = List.of();

		ZhiPuAiAssistantMessage message = new ZhiPuAiAssistantMessage(content, reasoningContent, properties, toolCalls,
				media, false);

		assertThatNoException().isThrownBy(message::toString);
		assertThat(message.toString()).contains(content, reasoningContent);
	}

	@Test
	public void testBuilderComplete() {
		Map<String, Object> properties = Map.of("key", "value");
		List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "testFunction", "{}"));
		List<Media> media = List.of();

		ZhiPuAiAssistantMessage.Builder builder = new ZhiPuAiAssistantMessage.Builder();
		ZhiPuAiAssistantMessage message = builder.content("content")
			.reasoningContent("reasoning")
			.properties(properties)
			.toolCalls(toolCalls)
			.media(media)
			.build();

		assertThat(message.getText()).isEqualTo("content");
		assertThat(message.getReasoningContent()).isEqualTo("reasoning");
		assertThat(message.getMetadata()).containsAllEntriesOf(properties);
		assertThat(message.getToolCalls()).isEqualTo(toolCalls);
		assertThat(message.getMedia()).isEqualTo(media);
	}

}
