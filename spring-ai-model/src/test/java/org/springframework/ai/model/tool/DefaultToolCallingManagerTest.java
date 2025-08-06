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
		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(toolCall));
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
		AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(toolCall));
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

}
