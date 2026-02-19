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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link DefaultToolCallingManager} with empty/null arguments handling.
 *
 */
class DefaultToolCallingManagerTest {

	@Test
	void shouldHandleNullArgumentsInStreamMode() {
		// Create a mock tool callback
		ToolCallback mockToolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("testTool")
					.description("A test tool")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				// Verify the input is not null or empty
				assertThat(toolInput).isNotNull();
				assertThat(toolInput).isNotEmpty();
				return "{\"result\": \"success\"}";
			}
		};

		// Create a ToolCall with empty parameters
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "function", "testTool", null);

		// Create a ChatResponse
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// Create a Prompt with tool callbacks
		Prompt prompt = new Prompt(List.of(new UserMessage("test")));

		// Mock the tool callbacks resolution by creating a custom ToolCallbackResolver
		DefaultToolCallingManager managerWithCallback = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> {
				if ("testTool".equals(toolName)) {
					return mockToolCallback;
				}
				return null;
			})
			.build();

		// Verify that no exception is thrown
		assertThatNoException().isThrownBy(() -> managerWithCallback.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleEmptyArgumentsInStreamMode() {
		// Create a mock tool callback
		ToolCallback mockToolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("testTool")
					.description("A test tool")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				// Verify the input is not null or empty
				assertThat(toolInput).isNotNull();
				assertThat(toolInput).isNotEmpty();
				return "{\"result\": \"success\"}";
			}
		};

		// Create a ToolCall with empty parameters
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "function", "testTool", "");

		// Create a ChatResponse
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// Create a Prompt with tool callbacks
		Prompt prompt = new Prompt(List.of(new UserMessage("test")));

		// Mock the tool callbacks resolution by creating a custom ToolCallbackResolver
		DefaultToolCallingManager managerWithCallback = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> {
				if ("testTool".equals(toolName)) {
					return mockToolCallback;
				}
				return null;
			})
			.build();

		// Verify that no exception is thrown
		assertThatNoException().isThrownBy(() -> managerWithCallback.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleMultipleToolCallsInSingleResponse() {
		// Create mock tool callbacks
		ToolCallback toolCallback1 = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("tool1")
					.description("First tool")
					.inputSchema("{\"type\": \"object\", \"properties\": {\"param\": {\"type\": \"string\"}}}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				return "{\"result\": \"tool1_success\"}";
			}
		};

		ToolCallback toolCallback2 = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("tool2")
					.description("Second tool")
					.inputSchema("{\"type\": \"object\", \"properties\": {\"value\": {\"type\": \"number\"}}}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				return "{\"result\": \"tool2_success\"}";
			}
		};

		// Create multiple ToolCalls
		AssistantMessage.ToolCall toolCall1 = new AssistantMessage.ToolCall("1", "function", "tool1",
				"{\"param\": \"test\"}");
		AssistantMessage.ToolCall toolCall2 = new AssistantMessage.ToolCall("2", "function", "tool2",
				"{\"value\": 42}");

		// Create ChatResponse with multiple tool calls
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall1, toolCall2))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		Prompt prompt = new Prompt(List.of(new UserMessage("test multiple tools")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> {
				if ("tool1".equals(toolName)) {
					return toolCallback1;
				}
				if ("tool2".equals(toolName)) {
					return toolCallback2;
				}
				return null;
			})
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleToolCallWithComplexJsonArguments() {
		ToolCallback complexToolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("complexTool")
					.description("A tool with complex JSON input")
					.inputSchema("{\"type\": \"object\", \"properties\": {\"nested\": {\"type\": \"object\"}}}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				assertThat(toolInput).contains("nested");
				assertThat(toolInput).contains("array");
				return "{\"result\": \"processed\"}";
			}
		};

		String complexJson = "{\"nested\": {\"level1\": {\"level2\": \"value\"}}, \"array\": [1, 2, 3]}";
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "function", "complexTool", complexJson);

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		Prompt prompt = new Prompt(List.of(new UserMessage("test complex json")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> "complexTool".equals(toolName) ? complexToolCallback : null)
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleToolCallWithMalformedJson() {
		ToolCallback toolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("testTool")
					.description("Test tool")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				// Should still receive some input even if malformed
				assertThat(toolInput).isNotNull();
				return "{\"result\": \"handled\"}";
			}
		};

		// Malformed JSON as tool arguments
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "function", "testTool",
				"{invalid json}");

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		Prompt prompt = new Prompt(List.of(new UserMessage("test malformed json")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> "testTool".equals(toolName) ? toolCallback : null)
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleToolCallReturningNull() {
		ToolCallback toolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("nullReturningTool")
					.description("Tool that returns null")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				return null; // Return null
			}
		};

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "function", "nullReturningTool", "{}");

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall))
			.build();
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		Prompt prompt = new Prompt(List.of(new UserMessage("test null return")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> "nullReturningTool".equals(toolName) ? toolCallback : null)
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleMultipleGenerationsWithToolCalls() {
		ToolCallback toolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("multiGenTool")
					.description("Tool for multiple generations")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				return "{\"result\": \"success\"}";
			}
		};

		// Create multiple generations with tool calls
		AssistantMessage.ToolCall toolCall1 = new AssistantMessage.ToolCall("1", "function", "multiGenTool", "{}");
		AssistantMessage.ToolCall toolCall2 = new AssistantMessage.ToolCall("2", "function", "multiGenTool", "{}");

		AssistantMessage assistantMessage1 = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall1))
			.build();

		AssistantMessage assistantMessage2 = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall2))
			.build();

		Generation generation1 = new Generation(assistantMessage1);
		Generation generation2 = new Generation(assistantMessage2);

		ChatResponse chatResponse = new ChatResponse(List.of(generation1, generation2));

		Prompt prompt = new Prompt(List.of(new UserMessage("test multiple generations")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> "multiGenTool".equals(toolName) ? toolCallback : null)
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

	@Test
	void shouldHandleMultipleGenerationsWithToolCallsWhenNameIsEmpty() {
		ToolCallback toolCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("multiGenTool")
					.description("Tool for multiple generations")
					.inputSchema("{}")
					.build();
			}

			@Override
			public ToolMetadata getToolMetadata() {
				return ToolMetadata.builder().build();
			}

			@Override
			public String call(String toolInput) {
				return "{\"result\": \"success\"}";
			}
		};

		// Create multiple generations with tool calls
		AssistantMessage.ToolCall toolCall1 = new AssistantMessage.ToolCall("1", "function", "multiGenTool", "{}");
		AssistantMessage.ToolCall toolCall2 = new AssistantMessage.ToolCall("2", "function", "multiGenTool", "{}");
		AssistantMessage.ToolCall toolCall3 = new AssistantMessage.ToolCall("3", "function", "", "{}");

		AssistantMessage assistantMessage1 = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall1))
			.build();

		AssistantMessage assistantMessage2 = AssistantMessage.builder()
			.content("")
			.properties(Map.of())
			.toolCalls(List.of(toolCall2, toolCall3))
			.build();

		Generation generation1 = new Generation(assistantMessage1);
		Generation generation2 = new Generation(assistantMessage2);

		ChatResponse chatResponse = new ChatResponse(List.of(generation1, generation2));

		Prompt prompt = new Prompt(List.of(new UserMessage("test multiple generations")));

		DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.toolCallbackResolver(toolName -> "multiGenTool".equals(toolName) ? toolCallback : null)
			.build();

		assertThatNoException().isThrownBy(() -> manager.executeToolCalls(prompt, chatResponse));
	}

}
