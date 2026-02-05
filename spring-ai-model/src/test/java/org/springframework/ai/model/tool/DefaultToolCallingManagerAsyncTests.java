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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.AsyncToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolCallingManager}'s async tool execution.
 *
 * @author Spring AI Team
 * @since 1.2.0
 */
class DefaultToolCallingManagerAsyncTests {

	private DefaultToolCallingManager toolCallingManager;

	@BeforeEach
	void setUp() {
		this.toolCallingManager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
			.toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder().build())
			.build();
	}

	@Test
	void testExecuteToolCallsAsyncWithAsyncToolCallback() {
		// Given: An async tool callback
		TestAsyncToolCallback asyncTool = new TestAsyncToolCallback("asyncTool", "Async result");

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "asyncTool", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(asyncTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: Verify the result
		assertThat(result).isNotNull();
		assertThat(result.conversationHistory()).hasSize(3); // user, assistant, tool
																// response
		assertThat(result.conversationHistory().get(2)).isInstanceOf(ToolResponseMessage.class);

		ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory().get(2);
		assertThat(toolResponse.getResponses()).hasSize(1);
		assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("Async result");
	}

	@Test
	void testExecuteToolCallsAsyncWithSyncToolCallback() {
		// Given: A sync tool callback (should be executed on boundedElastic)
		TestSyncToolCallback syncTool = new TestSyncToolCallback("syncTool", "Sync result");

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "syncTool", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(syncTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: Verify the result
		assertThat(result).isNotNull();
		assertThat(result.conversationHistory()).hasSize(3);
		ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory().get(2);
		assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("Sync result");
	}

	@Test
	void testExecuteToolCallsAsyncWithMixedTools() {
		// Given: Both async and sync tools
		TestAsyncToolCallback asyncTool = new TestAsyncToolCallback("asyncTool", "Async result");
		TestSyncToolCallback syncTool = new TestSyncToolCallback("syncTool", "Sync result");

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "asyncTool", "{}"),
						new AssistantMessage.ToolCall("id2", "function", "syncTool", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(asyncTool, syncTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: Verify both tools executed
		assertThat(result).isNotNull();
		ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory().get(2);
		assertThat(toolResponse.getResponses()).hasSize(2);
		assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("Async result");
		assertThat(toolResponse.getResponses().get(1).responseData()).isEqualTo("Sync result");
	}

	@Test
	void testExecuteToolCallsAsyncWithReturnDirectTrue() {
		// Given: Tool with returnDirect=true
		TestAsyncToolCallback asyncTool = new TestAsyncToolCallback("asyncTool", "Direct result", true);

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "asyncTool", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(asyncTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: returnDirect should be true
		assertThat(result).isNotNull();
		assertThat(result.returnDirect()).isTrue();
	}

	@Test
	void testExecuteToolCallsAsyncWithMultipleToolsReturnDirectLogic() {
		// Given: Multiple tools with mixed returnDirect
		TestAsyncToolCallback tool1 = new TestAsyncToolCallback("tool1", "Result1", true);
		TestAsyncToolCallback tool2 = new TestAsyncToolCallback("tool2", "Result2", false);

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "tool1", "{}"),
						new AssistantMessage.ToolCall("id2", "function", "tool2", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(tool1, tool2)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: returnDirect should be false (AND logic: true && false = false)
		assertThat(result).isNotNull();
		assertThat(result.returnDirect()).isFalse();
	}

	@Test
	void testExecuteToolCallsAsyncWithAsyncToolError() {
		// Given: Async tool that throws error
		AsyncToolCallback failingTool = new AsyncToolCallback() {
			@Override
			public Mono<String> callAsync(String toolInput, ToolContext context) {
				return Mono.error(new ToolExecutionException(getToolDefinition(), new RuntimeException("Async error")));
			}

			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder().name("failingTool").inputSchema("{}").build();
			}
		};

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "failingTool", "{}")));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(failingTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: Error should be processed
		assertThat(result).isNotNull();
		ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory().get(2);
		assertThat(toolResponse.getResponses().get(0).responseData()).contains("Async error");
	}

	@Test
	void testExecuteToolCallsAsyncWithNullArguments() {
		// Given: Tool call with null arguments
		TestAsyncToolCallback asyncTool = new TestAsyncToolCallback("asyncTool", "Result");

		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("id1", "function", "asyncTool", null)));
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

		Prompt prompt = new Prompt(new UserMessage("Test"),
				DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(asyncTool)).build());

		// When: Execute tools asynchronously
		ToolExecutionResult result = this.toolCallingManager.executeToolCallsAsync(prompt, chatResponse).block();

		// Then: Should use empty JSON object as default
		assertThat(result).isNotNull();
		ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory().get(2);
		assertThat(toolResponse.getResponses()).hasSize(1);
	}

	/**
	 * Test implementation of AsyncToolCallback.
	 */
	static class TestAsyncToolCallback implements AsyncToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		private final String result;

		TestAsyncToolCallback(String name, String result) {
			this(name, result, false);
		}

		TestAsyncToolCallback(String name, String result, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
			this.result = result;
		}

		@Override
		public Mono<String> callAsync(String toolInput, ToolContext context) {
			return Mono.just(this.result);
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

	}

	/**
	 * Test implementation of synchronous ToolCallback.
	 */
	static class TestSyncToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		private final String result;

		TestSyncToolCallback(String name, String result) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
			this.result = result;
		}

		@Override
		public String call(String toolInput) {
			return call(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext context) {
			return this.result;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

	}

}
