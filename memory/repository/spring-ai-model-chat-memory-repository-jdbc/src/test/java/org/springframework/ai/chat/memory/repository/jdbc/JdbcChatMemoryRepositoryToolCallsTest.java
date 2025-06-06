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

package org.springframework.ai.chat.memory.repository.jdbc;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.ModelOptionsUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for tool calls serialization and deserialization in
 * JdbcChatMemoryRepository.
 *
 * @author DoHoon Kim
 */
public class JdbcChatMemoryRepositoryToolCallsTest {

	@Test
	void testToolCallsJsonSerialization() throws Exception {
		// Create tool calls
		List<AssistantMessage.ToolCall> toolCalls = List.of(
				new AssistantMessage.ToolCall("call_1", "function", "get_weather", "{\"location\":\"Seoul\"}"),
				new AssistantMessage.ToolCall("call_2", "function", "get_time", "{\"timezone\":\"Asia/Seoul\"}"));

		// Serialize to JSON
		String json = ModelOptionsUtils.toJsonString(toolCalls);
		assertThat(json).isNotNull();
		assertThat(json).contains("call_1");
		assertThat(json).contains("get_weather");
		assertThat(json).contains("Seoul");

		// Deserialize from JSON
		List<AssistantMessage.ToolCall> deserializedToolCalls = ModelOptionsUtils.OBJECT_MAPPER.readValue(json,
				ModelOptionsUtils.OBJECT_MAPPER.getTypeFactory()
					.constructCollectionType(List.class, AssistantMessage.ToolCall.class));

		assertThat(deserializedToolCalls).hasSize(2);

		// Verify first tool call
		AssistantMessage.ToolCall firstToolCall = deserializedToolCalls.get(0);
		assertThat(firstToolCall.id()).isEqualTo("call_1");
		assertThat(firstToolCall.type()).isEqualTo("function");
		assertThat(firstToolCall.name()).isEqualTo("get_weather");
		assertThat(firstToolCall.arguments()).isEqualTo("{\"location\":\"Seoul\"}");

		// Verify second tool call
		AssistantMessage.ToolCall secondToolCall = deserializedToolCalls.get(1);
		assertThat(secondToolCall.id()).isEqualTo("call_2");
		assertThat(secondToolCall.type()).isEqualTo("function");
		assertThat(secondToolCall.name()).isEqualTo("get_time");
		assertThat(secondToolCall.arguments()).isEqualTo("{\"timezone\":\"Asia/Seoul\"}");
	}

	@Test
	void testEmptyToolCallsList() throws Exception {
		List<AssistantMessage.ToolCall> emptyToolCalls = List.of();

		String json = ModelOptionsUtils.toJsonString(emptyToolCalls);
		assertThat(json).isEqualTo("[]");

		List<AssistantMessage.ToolCall> deserializedToolCalls = ModelOptionsUtils.OBJECT_MAPPER.readValue(json,
				ModelOptionsUtils.OBJECT_MAPPER.getTypeFactory()
					.constructCollectionType(List.class, AssistantMessage.ToolCall.class));

		assertThat(deserializedToolCalls).isEmpty();
	}

	@Test
	void testAssistantMessageWithToolCalls() {
		List<AssistantMessage.ToolCall> toolCalls = List
			.of(new AssistantMessage.ToolCall("call_1", "function", "get_weather", "{\"location\":\"Seoul\"}"));

		AssistantMessage message = new AssistantMessage("I'll check the weather for you.", Map.of(), toolCalls);

		assertThat(message.hasToolCalls()).isTrue();
		assertThat(message.getToolCalls()).hasSize(1);
		assertThat(message.getToolCalls().get(0).name()).isEqualTo("get_weather");
	}

	@Test
	void testAssistantMessageWithoutToolCalls() {
		AssistantMessage message = new AssistantMessage("Simple response without tool calls.");

		assertThat(message.hasToolCalls()).isFalse();
		assertThat(message.getToolCalls()).isEmpty();
	}

}
