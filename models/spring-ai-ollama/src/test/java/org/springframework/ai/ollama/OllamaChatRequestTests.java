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

package org.springframework.ai.ollama;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Nicolas Krier
 */
class OllamaChatRequestTests {

	private final OllamaChatModel chatModel = OllamaChatModel.builder()
		.ollamaApi(OllamaApi.builder().build())
		.defaultOptions(OllamaChatOptions.builder().model("MODEL_NAME").topK(99).temperature(66.6).numGPU(1).build())
		.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
		.build();

	@Test
	void whenToolRuntimeOptionsThenMergeWithDefaults() {
		OllamaChatOptions defaultOptions = OllamaChatOptions.builder()
			.model("MODEL_NAME")
			.internalToolExecutionEnabled(true)
			.toolCallbacks(new TestToolCallback("tool1"), new TestToolCallback("tool2"))
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key1", "value1", "key2", "valueA"))
			.build();
		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.defaultOptions(defaultOptions)
			.build();

		OllamaChatOptions runtimeOptions = OllamaChatOptions.builder()
			.internalToolExecutionEnabled(false)
			.toolCallbacks(new TestToolCallback("tool3"), new TestToolCallback("tool4"))
			.toolNames("tool3")
			.toolContext(Map.of("key2", "valueB"))
			.build();
		Prompt prompt = chatModel.buildRequestPrompt(new Prompt("Test message content", runtimeOptions));

		assertThat(((ToolCallingChatOptions) prompt.getOptions())).isNotNull();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getInternalToolExecutionEnabled()).isFalse();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()).hasSize(2);
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()
			.stream()
			.map(toolCallback -> toolCallback.getToolDefinition().name())).containsExactlyInAnyOrder("tool3", "tool4");
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolNames()).containsExactlyInAnyOrder("tool3");
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "valueB");
	}

	@Test
	void createRequestWithDefaultOptions() {
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content"));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(66.6);
		assertThat(request.options().get("top_k")).isEqualTo(99);
		assertThat(request.options().get("num_gpu")).isEqualTo(1);
		assertThat(request.options().get("top_p")).isNull();
	}

	@Test
	void createRequestWithPromptOllamaOptions() {
		// Runtime options should override the default options.
		OllamaChatOptions promptOptions = OllamaChatOptions.builder().temperature(0.8).topP(0.5).numGPU(2).build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		var request = this.chatModel.ollamaChatRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(0.8);
		assertThat(request.options().get("top_k")).isEqualTo(99); // still the default
		// value.
		assertThat(request.options().get("num_gpu")).isEqualTo(2);
		assertThat(request.options().get("top_p")).isEqualTo(0.5); // new field introduced
		// by the
		// promptOptions.
	}

	@Test
	void createRequestWithPromptOllamaChatOptions() {
		// Runtime options should override the default options.
		OllamaChatOptions promptOptions = OllamaChatOptions.builder().temperature(0.8).topP(0.5).numGPU(2).build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		var request = this.chatModel.ollamaChatRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(0.8);
		assertThat(request.options().get("top_k")).isEqualTo(99); // still the default
		// value.
		assertThat(request.options().get("num_gpu")).isEqualTo(2);
		assertThat(request.options().get("top_p")).isEqualTo(0.5); // new field introduced
		// by the
		// promptOptions.
	}

	@Test
	public void createRequestWithPromptPortableChatOptions() {
		// Ollama runtime options.
		ChatOptions portablePromptOptions = ChatOptions.builder().temperature(0.9).topK(100).topP(0.6).build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content", portablePromptOptions));

		var request = this.chatModel.ollamaChatRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("MODEL_NAME");
		assertThat(request.options().get("temperature")).isEqualTo(0.9);
		assertThat(request.options().get("top_k")).isEqualTo(100);
		assertThat(request.options().get("num_gpu")).isEqualTo(1); // default value.
		assertThat(request.options().get("top_p")).isEqualTo(0.6);
	}

	@Test
	public void createRequestWithPromptOptionsModelOverride() {
		// Ollama runtime options.
		OllamaChatOptions promptOptions = OllamaChatOptions.builder().model("PROMPT_MODEL").build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		var request = this.chatModel.ollamaChatRequest(prompt, true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

	@Test
	public void createRequestWithDefaultOptionsModelOverride() {
		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.defaultOptions(OllamaChatOptions.builder().model("DEFAULT_OPTIONS_MODEL").build())
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		var prompt1 = chatModel.buildRequestPrompt(new Prompt("Test message content"));

		var request = chatModel.ollamaChatRequest(prompt1, true);

		assertThat(request.model()).isEqualTo("DEFAULT_OPTIONS_MODEL");

		// Prompt options should override the default options.
		OllamaChatOptions promptOptions = OllamaChatOptions.builder().model("PROMPT_MODEL").build();
		var prompt2 = chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		request = chatModel.ollamaChatRequest(prompt2, true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

	@Test
	public void createRequestWithDefaultOptionsModelChatOptionsOverride() {
		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.defaultOptions(OllamaChatOptions.builder().model("DEFAULT_OPTIONS_MODEL").build())
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		var prompt1 = chatModel.buildRequestPrompt(new Prompt("Test message content"));

		var request = chatModel.ollamaChatRequest(prompt1, true);

		assertThat(request.model()).isEqualTo("DEFAULT_OPTIONS_MODEL");

		// Prompt options should override the default options.
		OllamaChatOptions promptOptions = OllamaChatOptions.builder().model("PROMPT_MODEL").build();
		var prompt2 = chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		request = chatModel.ollamaChatRequest(prompt2, true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

	@Test
	void createRequestWithAllMessageTypes() {
		var prompt = this.chatModel.buildRequestPrompt(new Prompt(createMessagesWithAllMessageTypes()));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		assertThat(request.messages()).hasSize(6);

		var ollamaSystemMessage = request.messages().get(0);
		assertThat(ollamaSystemMessage.role()).isEqualTo(OllamaApi.Message.Role.SYSTEM);
		assertThat(ollamaSystemMessage.content()).isEqualTo("Test system message");

		var ollamaUserMessage = request.messages().get(1);
		assertThat(ollamaUserMessage.role()).isEqualTo(OllamaApi.Message.Role.USER);
		assertThat(ollamaUserMessage.content()).isEqualTo("Test user message");

		var ollamaToolResponse1 = request.messages().get(2);
		assertThat(ollamaToolResponse1.role()).isEqualTo(OllamaApi.Message.Role.TOOL);
		assertThat(ollamaToolResponse1.content()).isEqualTo("Test tool response 1");

		var ollamaToolResponse2 = request.messages().get(3);
		assertThat(ollamaToolResponse2.role()).isEqualTo(OllamaApi.Message.Role.TOOL);
		assertThat(ollamaToolResponse2.content()).isEqualTo("Test tool response 2");

		var ollamaToolResponse3 = request.messages().get(4);
		assertThat(ollamaToolResponse3.role()).isEqualTo(OllamaApi.Message.Role.TOOL);
		assertThat(ollamaToolResponse3.content()).isEqualTo("Test tool response 3");

		var ollamaAssistantMessage = request.messages().get(5);
		assertThat(ollamaAssistantMessage.role()).isEqualTo(OllamaApi.Message.Role.ASSISTANT);
		assertThat(ollamaAssistantMessage.content()).isEqualTo("Test assistant message");
	}

	private static List<Message> createMessagesWithAllMessageTypes() {
		var systemMessage = new SystemMessage("Test system message");
		var userMessage = new UserMessage("Test user message");
		// @formatter:off
		var toolResponseMessage = ToolResponseMessage.builder().responses(List.of(
				new ToolResponse("tool1", "Tool 1", "Test tool response 1"),
				new ToolResponse("tool2", "Tool 2", "Test tool response 2"),
				new ToolResponse("tool3", "Tool 3", "Test tool response 3"))).build();
		// @formatter:on
		var assistantMessage = new AssistantMessage("Test assistant message");

		return List.of(systemMessage, userMessage, toolResponseMessage, assistantMessage);
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
