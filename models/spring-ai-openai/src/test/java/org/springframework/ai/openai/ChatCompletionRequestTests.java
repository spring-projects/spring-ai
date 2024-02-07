/*
 * Copyright 2024-2024 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.AbstractToolFunctionCallback;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.chat.api.tool.MockWeatherService;
import org.springframework.ai.openai.chat.api.tool.MockWeatherService.Request;
import org.springframework.ai.openai.chat.api.tool.MockWeatherService.Response;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class ChatCompletionRequestTests {

	@Test
	public void createRequestWithChatOptions() {

		var client = new OpenAiChatClient(new OpenAiApi("TEST"),
				OpenAiChatOptions.builder().withModel("DEFAULT_MODEL").withTemperature(66.6f).build());

		var request = client.createRequest(new Prompt("Test message content"), false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6f);

		request = client.createRequest(new Prompt("Test message content",
				OpenAiChatOptions.builder().withModel("PROMPT_MODEL").withTemperature(99.9f).build()), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9f);
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new OpenAiChatClient(new OpenAiApi("TEST"))
			.withDefaultOptions(OpenAiChatOptions.builder().withModel("DEFAULT_MODEL").build());

		var request = client.createRequest(new Prompt("Test message content", OpenAiChatOptions.builder()
			.withModel("PROMPT_MODEL")
			.withToolCallbacks(
					List.of(new AbstractToolFunctionCallback<MockWeatherService.Request, MockWeatherService.Response>(
							TOOL_FUNCTION_NAME, "Get the weather in location", MockWeatherService.Request.class) {
						@Override
						public Response apply(Request request) {
							return new MockWeatherService().apply(request);
						}
					}))
			.build()), false);

		assertThat(client.getToolCallbackRegister()).hasSize(1);
		assertThat(client.getToolCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("PROMPT_MODEL");

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).function().name()).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void defaultOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new OpenAiChatClient(new OpenAiApi("TEST")).withDefaultOptions(OpenAiChatOptions.builder()
			.withModel("DEFAULT_MODEL")
			.withToolCallbacks(
					List.of(new AbstractToolFunctionCallback<MockWeatherService.Request, MockWeatherService.Response>(
							TOOL_FUNCTION_NAME, "Get the weather in location", MockWeatherService.Request.class) {
						@Override
						public Response apply(Request request) {
							return new MockWeatherService().apply(request);
						}
					}))
			.build());

		var request = client.createRequest(new Prompt("Test message content"), false);

		assertThat(client.getToolCallbackRegister()).hasSize(1);
		assertThat(client.getToolCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);
		assertThat(client.getToolCallbackRegister().get(TOOL_FUNCTION_NAME).getDescription())
			.isEqualTo("Get the weather in location");

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");

		assertThat(request.tools()).as("Default Options callback functions are not automatically enabled!")
			.isNullOrEmpty();

		// Explicitly enable the function
		request = client.createRequest(new Prompt("Test message content",
				OpenAiChatOptions.builder().withEnabledFunction(TOOL_FUNCTION_NAME).build()), false);

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).function().name()).as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		// Override the default options function with one from the prompt
		request = client
			.createRequest(new Prompt("Test message content",
					OpenAiChatOptions.builder()
						.withToolCallbacks(List
							.of(new AbstractToolFunctionCallback<MockWeatherService.Request, String>(TOOL_FUNCTION_NAME,
									"Overridden function description", MockWeatherService.Request.class) {
								@Override
								public String apply(Request request) {
									return "Mock response";
								}
							}))
						.build()),
					false);

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).function().name()).as("Explicitly enabled function")
			.isEqualTo(TOOL_FUNCTION_NAME);

		assertThat(client.getToolCallbackRegister()).hasSize(1);
		assertThat(client.getToolCallbackRegister()).containsKeys(TOOL_FUNCTION_NAME);
		assertThat(client.getToolCallbackRegister().get(TOOL_FUNCTION_NAME).getDescription())
			.isEqualTo("Overridden function description");
	}

}
