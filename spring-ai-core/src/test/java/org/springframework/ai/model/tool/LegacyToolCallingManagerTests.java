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

package org.springframework.ai.model.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link LegacyToolCallingManager}.
 *
 * @author Thomas Vitale
 */
class LegacyToolCallingManagerTests {

	// RESOLVE TOOL DEFINITIONS

	@Test
	void whenChatOptionsIsNullThenThrow() {
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder().build();
		assertThatThrownBy(() -> toolCallingManager.resolveToolDefinitions(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatOptions cannot be null");
	}

	@Test
	void whenToolCallbackExistsThenResolve() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder()
			.functionCallbacks(List.of(toolCallback))
			.build();

		List<ToolDefinition> toolDefinitions = toolCallingManager
			.resolveToolDefinitions(ToolCallingChatOptions.builder().tools("toolA").build());

		assertThat(toolDefinitions).containsExactly(toolCallback.getToolDefinition());
	}

	@Test
	void whenToolCallbackDoesNotExistThenThrow() {
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder().functionCallbacks(List.of()).build();

		assertThatThrownBy(() -> toolCallingManager
			.resolveToolDefinitions(ToolCallingChatOptions.builder().tools("toolB").build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No ToolCallback found for tool name: toolB");
	}

	// EXECUTE TOOL CALLS

	@Test
	void whenPromptIsNullThenThrow() {
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder().build();
		assertThatThrownBy(() -> toolCallingManager.executeToolCalls(null, mock(ChatResponse.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenChatResponseIsNullThenThrow() {
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder().build();
		assertThatThrownBy(() -> toolCallingManager.executeToolCalls(mock(Prompt.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatResponse cannot be null");
	}

	@Test
	void whenNoToolCallInChatResponseThenThrow() {
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder().build();
		assertThatThrownBy(() -> toolCallingManager.executeToolCalls(mock(Prompt.class),
				ChatResponse.builder().generations(List.of()).build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No tool call requested by the chat model");
	}

	@Test
	void whenSingleToolCallInChatResponseThenExecute() {
		ToolCallback toolCallback = new LegacyToolCallingManagerTests.TestToolCallback("toolA");
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder()
			.functionCallbacks(List.of(toolCallback))
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolA", "toolA", "Mission accomplished!")));

		List<Message> toolCallHistory = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolCallHistory).contains(expectedToolResponse);
	}

	@Test
	void whenMultipleToolCallsInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new LegacyToolCallingManagerTests.TestToolCallback("toolA");
		ToolCallback toolCallbackB = new LegacyToolCallingManagerTests.TestToolCallback("toolB");
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder()
			.functionCallbacks(List.of(toolCallbackA, toolCallbackB))
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"),
							new AssistantMessage.ToolCall("toolB", "function", "toolB", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolA", "toolA", "Mission accomplished!"),
						new ToolResponseMessage.ToolResponse("toolB", "toolB", "Mission accomplished!")));

		List<Message> toolCallHistory = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolCallHistory).contains(expectedToolResponse);
	}

	@Test
	void whenToolCallWithExceptionThenReturnError() {
		ToolCallback toolCallback = new LegacyToolCallingManagerTests.FailingToolCallback("toolC");
		ToolCallingManager toolCallingManager = LegacyToolCallingManager.builder()
			.functionCallbacks(List.of(toolCallback))
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolC", "function", "toolC", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolC", "toolC", "You failed this city!")));

		List<Message> toolCallHistory = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolCallHistory).contains(expectedToolResponse);
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		public TestToolCallback(String name) {
			this.toolDefinition = ToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

	static class FailingToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		public FailingToolCallback(String name) {
			this.toolDefinition = ToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			throw new ToolExecutionException(toolDefinition, new IllegalStateException("You failed this city!"));
		}

	}

}
