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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

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
		boolean result = this.predicate.test(options, chatResponse);
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
		boolean result = this.predicate.test(options, chatResponse);
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
		boolean result = this.predicate.test(options, chatResponse);
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
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenRegularChatOptionsAndHasToolCalls() {
		// Create regular ChatOptions (not ToolCallingChatOptions)
		ChatOptions options = ChatOptions.builder().build();

		// Create a ChatResponse with tool calls
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate - should use default value (true) for internal tool
		// execution
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenNullChatResponse() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Test the predicate with null ChatResponse
		boolean result = this.predicate.test(options, null);
		assertThat(result).isFalse();
	}

	@Test
	void whenEmptyGenerationsList() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse with empty generations list
		ChatResponse chatResponse = new ChatResponse(List.of());

		// Test the predicate
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenMultipleGenerationsWithMixedToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create multiple generations - some with tool calls, some without
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id1", "function", "testTool", "{}");
		AssistantMessage messageWithToolCall = new AssistantMessage("test1", Map.of(), List.of(toolCall));
		AssistantMessage messageWithoutToolCall = new AssistantMessage("test2");

		ChatResponse chatResponse = new ChatResponse(
				List.of(new Generation(messageWithToolCall), new Generation(messageWithoutToolCall)));

		// Test the predicate - should return true if any generation has tool calls
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenMultipleGenerationsWithoutToolCalls() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create multiple generations without tool calls
		AssistantMessage message1 = new AssistantMessage("test1");
		AssistantMessage message2 = new AssistantMessage("test2");

		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(message1), new Generation(message2)));

		// Test the predicate
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenAssistantMessageHasEmptyToolCallsList() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse with AssistantMessage having empty tool calls list
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of());
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isFalse();
	}

	@Test
	void whenMultipleToolCallsPresent() {
		// Create a ToolCallingChatOptions with internal tool execution enabled
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();

		// Create a ChatResponse with multiple tool calls
		AssistantMessage.ToolCall toolCall1 = new AssistantMessage.ToolCall("id1", "function", "testTool1", "{}");
		AssistantMessage.ToolCall toolCall2 = new AssistantMessage.ToolCall("id2", "function", "testTool2",
				"{\"param\": \"value\"}");
		AssistantMessage assistantMessage = new AssistantMessage("test", Map.of(), List.of(toolCall1, toolCall2));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		// Test the predicate
		boolean result = this.predicate.test(options, chatResponse);
		assertThat(result).isTrue();
	}

}
