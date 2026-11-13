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

package org.springframework.ai.jitllm;

import java.util.List;

import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatMessage;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.FinishReason;
import org.beehive.jitllm.api.ToolSpec;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JitLlmConversions}.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmConversionsTests {

	@Test
	void systemUserAndAssistantTextMapToTheirRoles() {
		List<ChatMessage> messages = JitLlmConversions.toEngineMessages(
				List.of(new SystemMessage("be brief"), new UserMessage("hi"), new AssistantMessage("hello")));

		assertThat(messages).extracting(ChatMessage::role)
			.containsExactly(ChatRole.SYSTEM, ChatRole.USER, ChatRole.ASSISTANT);
		assertThat(messages.get(2).content()).containsExactly(new ChatContent.Text("hello"));
	}

	@Test
	void anAssistantToolCallAndItsResultKeepTheirIds() {
		AssistantMessage call = AssistantMessage.builder()
			.content("")
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Munich\"}")))
			.build();
		ToolResponseMessage result = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "\"sunny\"")))
			.build();

		List<ChatMessage> messages = JitLlmConversions.toEngineMessages(List.of(call, result));

		assertThat(messages.get(0).content())
			.containsExactly(new ChatContent.ToolCall("call_1", "getWeather", "{\"city\":\"Munich\"}"));
		assertThat(messages.get(1).role()).isEqualTo(ChatRole.TOOL);
		assertThat(messages.get(1).content())
			.containsExactly(new ChatContent.ToolResult("call_1", "getWeather", "sunny"));
	}

	@Test
	void aBlankIdIsGeneratedAndMissingArgumentsBecomeAnEmptyObject() {
		AssistantMessage call = AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "now", "")))
			.build();

		ChatContent.ToolCall converted = (ChatContent.ToolCall) JitLlmConversions.toEngineMessages(List.of(call))
			.get(0)
			.content()
			.get(0);

		assertThat(converted.id()).startsWith("call_");
		assertThat(converted.argumentsJson()).isEqualTo("{}");
	}

	@Test
	void anEmptyAssistantTurnStillEncodesSomething() {
		assertThat(JitLlmConversions.toEngineMessages(List.of(new AssistantMessage(""))).get(0).content())
			.containsExactly(new ChatContent.Text(""));
	}

	@Test
	void everyToolResponseInOneMessageBecomesItsOwnTurn() {
		ToolResponseMessage result = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("a", "one", "1"),
					new ToolResponseMessage.ToolResponse("b", "two", "2")))
			.build();

		assertThat(JitLlmConversions.toEngineMessages(List.of(result))).hasSize(2);
	}

	@Test
	void toolDefinitionsCarryNameDescriptionAndSchema() {
		ToolDefinition tool = ToolDefinition.builder()
			.name("getWeather")
			.description("Current weather")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
			.build();

		assertThat(JitLlmConversions.toEngineTools(List.of(tool))).containsExactly(new ToolSpec("getWeather",
				"Current weather", "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}"));
	}

	@Test
	void engineToolCallsBecomeSpringToolCallsInOrder() {
		assertThat(JitLlmConversions.toSpringToolCalls(
				List.of(new ChatContent.ToolCall("a", "one", "{}"), new ChatContent.ToolCall("b", "two", "{\"x\":1}"))))
			.containsExactly(new AssistantMessage.ToolCall("a", "function", "one", "{}"),
					new AssistantMessage.ToolCall("b", "function", "two", "{\"x\":1}"));
	}

	@Test
	void everyEngineFinishReasonIsMapped() {
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.STOP_TOKEN)).isEqualTo("STOP");
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.STOP_SEQUENCE)).isEqualTo("STOP");
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.MAX_TOKENS)).isEqualTo("LENGTH");
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.CONTEXT_FULL)).isEqualTo("LENGTH");
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.TOOL_CALL)).isEqualTo("TOOL_CALLS");
		assertThat(JitLlmConversions.toSpringFinishReason(FinishReason.CANCELLED)).isEqualTo("CANCELLED");
	}

	@Test
	void aJsonStringToolResultIsUnwrappedAndAnythingElsePassesThrough() {
		assertThat(JitLlmConversions.unwrapToolResult("\"sunny\"")).isEqualTo("sunny");
		assertThat(JitLlmConversions.unwrapToolResult("{\"temp\":21}")).isEqualTo("{\"temp\":21}");
		assertThat(JitLlmConversions.unwrapToolResult("\"unterminated")).isEqualTo("\"unterminated");
		assertThat(JitLlmConversions.unwrapToolResult(null)).isEmpty();
	}

}
