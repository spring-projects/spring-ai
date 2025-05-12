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
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultToolCallingManager}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallingManagerTests {

	// BUILD

	@Test
	void whenDefaultArgumentsThenReturn() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThat(defaultToolExecutor).isNotNull();
	}

	@Test
	void whenObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(null)
			.toolCallbackResolver(mock(ToolCallbackResolver.class))
			.toolExecutionExceptionProcessor(mock(ToolExecutionExceptionProcessor.class))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("observationRegistry cannot be null");
	}

	@Test
	void whenToolCallbackResolverIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(mock(ObservationRegistry.class))
			.toolCallbackResolver(null)
			.toolExecutionExceptionProcessor(mock(ToolExecutionExceptionProcessor.class))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("toolCallbackResolver cannot be null");
	}

	@Test
	void whenToolCallExceptionConverterIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolCallingManager.builder()
			.observationRegistry(mock(ObservationRegistry.class))
			.toolCallbackResolver(mock(ToolCallbackResolver.class))
			.toolExecutionExceptionProcessor(null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallExceptionConverter cannot be null");
	}

	// RESOLVE TOOL DEFINITIONS

	@Test
	void whenChatOptionsIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.resolveToolDefinitions(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatOptions cannot be null");
	}

	@Test
	void whenToolCallbackExistsThenResolve() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		List<ToolDefinition> toolDefinitions = toolCallingManager
			.resolveToolDefinitions(ToolCallingChatOptions.builder().toolNames("toolA").build());

		assertThat(toolDefinitions).containsExactly(toolCallback.getToolDefinition());
	}

	@Test
	void whenToolCallbackDoesNotExistThenThrow() {
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of());
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		assertThatThrownBy(() -> toolCallingManager
			.resolveToolDefinitions(ToolCallingChatOptions.builder().toolNames("toolB").build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No ToolCallback found for tool name: toolB");
	}

	// EXECUTE TOOL CALLS

	@Test
	void whenPromptIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(null, mock(ChatResponse.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenChatResponseIsNullThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(mock(Prompt.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatResponse cannot be null");
	}

	@Test
	void whenNoToolCallInChatResponseThenThrow() {
		DefaultToolCallingManager defaultToolExecutor = DefaultToolCallingManager.builder().build();
		assertThatThrownBy(() -> defaultToolExecutor.executeToolCalls(mock(Prompt.class),
				ChatResponse.builder().generations(List.of()).build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No tool call requested by the chat model");
	}

	@Test
	void whenSingleToolCallInChatResponseThenExecute() {
		ToolCallback toolCallback = new TestToolCallback("toolA");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolA", "toolA", "Mission accomplished!")));

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenSingleToolCallWithReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallback = new TestToolCallback("toolA", true);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolA", "toolA", "Mission accomplished!")));

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isTrue();
	}

	@Test
	void whenMultipleToolCallsInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA");
		ToolCallback toolCallbackB = new TestToolCallback("toolB");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
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

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenDuplicateMixedToolCallsInChatResponseThenExecute() {
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt(new UserMessage("Hello"),
				ToolCallingChatOptions.builder()
					.toolCallbacks(new TestToolCallback("toolA"))
					.toolNames("toolA")
					.build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolA", "toolA", "Mission accomplished!")));

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	@Test
	void whenMultipleToolCallsWithReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA", true);
		ToolCallback toolCallbackB = new TestToolCallback("toolB", true);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
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

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isTrue();
	}

	@Test
	void whenMultipleToolCallsWithMixedReturnDirectInChatResponseThenExecute() {
		ToolCallback toolCallbackA = new TestToolCallback("toolA", true);
		ToolCallback toolCallbackB = new TestToolCallback("toolB", false);
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(
				List.of(toolCallbackA, toolCallbackB));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
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

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
		assertThat(toolExecutionResult.returnDirect()).isFalse();
	}

	@Test
	void whenToolCallWithExceptionThenReturnError() {
		ToolCallback toolCallback = new FailingToolCallback("toolC");
		ToolCallbackResolver toolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
			.toolCallbackResolver(toolCallbackResolver)
			.build();

		Prompt prompt = new Prompt(new UserMessage("Hello"), ToolCallingChatOptions.builder().build());
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolC", "function", "toolC", "{}"))))))
			.build();

		ToolResponseMessage expectedToolResponse = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse("toolC", "toolC", "You failed this city!")));

		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).contains(expectedToolResponse);
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

	static class FailingToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		FailingToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			throw new ToolExecutionException(this.toolDefinition, new IllegalStateException("You failed this city!"));
		}

	}

}
