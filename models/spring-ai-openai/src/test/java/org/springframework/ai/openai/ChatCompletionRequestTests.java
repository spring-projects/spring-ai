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

package org.springframework.ai.openai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class ChatCompletionRequestTests {

	@Test
	void whenToolRuntimeOptionsThenMergeWithDefaults() {
		OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.internalToolExecutionEnabled(true)
			.toolCallbacks(new TestToolCallback("tool1"), new TestToolCallback("tool2"))
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key1", "value1", "key2", "valueA"))
			.build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey(new SimpleApiKey("TEST")).build())
			.defaultOptions(defaultOptions)
			.build();

		OpenAiChatOptions runtimeOptions = OpenAiChatOptions.builder()
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
	void createRequestWithChatOptions() {
		var client = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey("TEST").build())
			.defaultOptions(OpenAiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build())
			.build();

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6);

		request = client.createRequest(new Prompt("Test message content",
				OpenAiChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).build()), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9);
	}

	@Test
	void promptOptionsTools() {
		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey("TEST").build())
			.defaultOptions(OpenAiChatOptions.builder().model("DEFAULT_MODEL").build())
			.build();

		var prompt = client.buildRequestPrompt(new Prompt("Test message content",
				OpenAiChatOptions.builder()
					.model("PROMPT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("PROMPT_MODEL");

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	void defaultOptionsTools() {
		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey("TEST").build())
			.defaultOptions(OpenAiChatOptions.builder()
				.model("DEFAULT_MODEL")
				.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build())
			.build();

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);

		// Reference the default options tool by name at runtime
		prompt = client.buildRequestPrompt(
				new Prompt("Test message content", OpenAiChatOptions.builder().toolNames(TOOL_FUNCTION_NAME).build()));
		request = client.createRequest(prompt, false);

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);
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
