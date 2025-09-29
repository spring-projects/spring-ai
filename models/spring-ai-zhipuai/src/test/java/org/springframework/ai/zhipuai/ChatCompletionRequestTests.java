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

package org.springframework.ai.zhipuai;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.zhipuai.api.MockWeatherService;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
public class ChatCompletionRequestTests {

	@Test
	public void createRequestWithChatOptions() {

		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build());

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6);

		request = client.createRequest(new Prompt("Test message content",
				ZhiPuAiChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).build()), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9);
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder().model("DEFAULT_MODEL").build());

		var request = client.createRequest(new Prompt("Test message content",
				ZhiPuAiChatOptions.builder()
					.model("PROMPT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()),
				false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("PROMPT_MODEL");

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void defaultOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder()
					.model("DEFAULT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build());

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
	}

	@Test
	public void promptOptionsOverrideDefaultOptions() {
		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder().model("DEFAULT_MODEL").temperature(10.0).build());

		var request = client.createRequest(new Prompt("Test", ZhiPuAiChatOptions.builder().temperature(90.0).build()),
				false);

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(90.0);
	}

	@Test
	public void defaultOptionsToolsWithAssertion() {
		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder()
					.model("DEFAULT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build());

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));
		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void createRequestWithStreamingEnabled() {
		var client = new ZhiPuAiChatModel(ZhiPuAiApi.builder().apiKey("TEST").build(),
				ZhiPuAiChatOptions.builder().model("DEFAULT_MODEL").build());

		var prompt = client.buildRequestPrompt(new Prompt("Test streaming"));
		var request = client.createRequest(prompt, true);

		assertThat(request.stream()).isTrue();
		assertThat(request.messages()).hasSize(1);
	}

}
