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

package org.springframework.ai.google.genai.schema;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link GoogleGenAiToolCallingManager}, focused on how it composes with
 * tool call limits configured on the delegate {@link ToolCallingManager}.
 *
 * @author Christian Tzolov
 */
class GoogleGenAiToolCallingManagerTests {

	@Test
	void executeToolCallsDelegatesToWrappedManager() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager delegate = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.build();
		GoogleGenAiToolCallingManager manager = new GoogleGenAiToolCallingManager(delegate);

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = chatResponseRequestingTool("toolA");

		ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);

		assertThat(result.conversationHistory()).contains(ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("toolA", "toolA", "Mission accomplished!")))
			.build());
	}

	@Test
	void executeToolCallsPropagatesToolCallLimitExceededExceptionFromDelegate() {
		// GoogleGenAiToolCallingManager.executeToolCalls has no try/catch of its own -
		// it is a one-line delegate - so ToolCallLimitExceededException thrown by the
		// wrapped manager (the default THROW behavior) must propagate through it
		// completely unchanged, exactly as it would if the delegate were called
		// directly. This proves that composition without needing any dedicated
		// exception handling in GoogleGenAiToolCallingManager itself.
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager delegate = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.resolutionFallbackEnabled(true)
			.maxCallsPerTool("toolA", 1)
			.build();
		GoogleGenAiToolCallingManager manager = new GoogleGenAiToolCallingManager(delegate);

		// One prior call to toolA already recorded in this turn's history.
		ToolResponseMessage priorToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("priorId", "toolA", "Mission accomplished!")))
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello"), priorToolResponse),
				ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = chatResponseRequestingTool("toolA");

		assertThatExceptionOfType(ToolCallLimitExceededException.class)
			.isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse))
			.satisfies(ex -> {
				assertThat(ex.getToolName()).isEqualTo("toolA");
				assertThat(ex.getLimit()).isEqualTo(1);
				assertThat(ex.getPartialToolExecutionResult()).isNotNull();
			});
	}

	private static ChatResponse chatResponseRequestingTool(String toolName) {
		return ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new AssistantMessage.ToolCall(toolName, "function", toolName, "{}")))
				.build())))
			.build();
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
