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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class OllamaChatRequestTests {

	OllamaChatModel chatModel = OllamaChatModel.builder()
		.ollamaApi(new OllamaApi())
		.defaultOptions(OllamaOptions.builder().model("MODEL_NAME").topK(99).temperature(66.6).numGPU(1).build())
		.build();

	@Test
	void whenToolRuntimeOptionsThenMergeWithDefaults() {
		OllamaOptions defaultOptions = OllamaOptions.builder()
			.model("MODEL_NAME")
			.internalToolExecutionEnabled(true)
			.toolCallbacks(new TestToolCallback("tool1"), new TestToolCallback("tool2"))
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key1", "value1"))
			.build();
		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(new OllamaApi())
			.defaultOptions(defaultOptions)
			.build();

		OllamaOptions runtimeOptions = OllamaOptions.builder()
			.internalToolExecutionEnabled(false)
			.toolCallbacks(new TestToolCallback("tool3"), new TestToolCallback("tool4"))
			.toolNames("tool3")
			.toolContext(Map.of("key2", "value2"))
			.build();
		Prompt prompt = chatModel.buildRequestPrompt(new Prompt("Test message content", runtimeOptions));

		assertThat(((ToolCallingChatOptions) prompt.getOptions())).isNotNull();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).isInternalToolExecutionEnabled()).isFalse();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()).hasSize(4);
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolNames()).containsExactlyInAnyOrder("tool1",
				"tool2", "tool3");
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2");
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
		OllamaOptions promptOptions = OllamaOptions.builder().temperature(0.8).topP(0.5).numGPU(2).build();
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
		OllamaOptions promptOptions = OllamaOptions.builder().model("PROMPT_MODEL").build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		var request = this.chatModel.ollamaChatRequest(prompt, true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
	}

	@Test
	public void createRequestWithDefaultOptionsModelOverride() {
		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(new OllamaApi())
			.defaultOptions(OllamaOptions.builder().model("DEFAULT_OPTIONS_MODEL").build())
			.build();

		var prompt1 = chatModel.buildRequestPrompt(new Prompt("Test message content"));

		var request = chatModel.ollamaChatRequest(prompt1, true);

		assertThat(request.model()).isEqualTo("DEFAULT_OPTIONS_MODEL");

		// Prompt options should override the default options.
		OllamaOptions promptOptions = OllamaOptions.builder().model("PROMPT_MODEL").build();
		var prompt2 = chatModel.buildRequestPrompt(new Prompt("Test message content", promptOptions));

		request = chatModel.ollamaChatRequest(prompt2, true);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
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

}
