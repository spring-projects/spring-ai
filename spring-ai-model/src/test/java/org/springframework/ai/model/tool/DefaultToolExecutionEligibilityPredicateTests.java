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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallingOptions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolExecutionEligibilityPredicate}.
 *
 * @author Christian Tzolov
 */
class DefaultToolExecutionEligibilityPredicateTests {

	private final DefaultToolExecutionEligibilityPredicate predicate = new DefaultToolExecutionEligibilityPredicate();

	@Test
	void whenToolExecutionEnabledAndHasToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenToolExecutionEnabledAndNoToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse without tool calls
		AssistantMessage assistantMessage = new AssistantMessage("test");
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenToolExecutionDisabledAndHasToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution disabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenToolExecutionDisabledAndNoToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution disabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();

		// Create a ChatResponse without tool calls
		AssistantMessage assistantMessage = new AssistantMessage("test");
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenFunctionCallingOptionsAndToolExecutionEnabled() {
		// Create a FunctionCallingOptions with proxy tool calls disabled (which means
		// internal tool execution is enabled)
		FunctionCallingOptions options = FunctionCallingOptions.builder().proxyToolCalls(false).build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenFunctionCallingOptionsAndToolExecutionDisabled() {
		// Create a FunctionCallingOptions with proxy tool calls enabled (which means
		// internal tool execution is disabled)
		FunctionCallingOptions options = FunctionCallingOptions.builder().proxyToolCalls(true).build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenRegularChatOptionsAndHasToolCalls() {
		// Create regular ChatOptions (not ToolCallingChatOptions or
		// FunctionCallingOptions)
		ChatOptions options = ChatOptions.builder().build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate - should use default value (true) for internal tool
		// execution
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenNullChatResponse() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Test the predicate with null ChatResponse
		boolean result = predicate.test(options, null);
		assertThat(result).isFalse();
	}

	@Test
	void whenEmptyGenerationsList() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse with empty generations list
		ChatResponse chatResponse = new ChatResponse(List.of());

		// Test the predicate
		boolean result = predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

}
