/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama.api.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.BaseOllamaIT;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
public class OllamaApiToolFunctionCallIT extends BaseOllamaIT {

	private static final String MODEL = "qwen2.5:3b";

	private static final Logger logger = LoggerFactory.getLogger(OllamaApiToolFunctionCallIT.class);

	static OllamaApi ollamaApi;

	MockWeatherService weatherService = new MockWeatherService();

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		ollamaApi = initializeOllama(MODEL);
	}

	@SuppressWarnings("null")
	@Test
	public void toolFunctionCall() {
		// Step 1: send the conversation and available functions to the model
		var message = Message.builder(Role.USER)
			.withContent(
					"What's the weather like in San Francisco, Tokyo, and Paris? Return a list with the temperature in Celsius for each of the three locations.")
			.build();

		var functionTool = new OllamaApi.ChatRequest.Tool(new OllamaApi.ChatRequest.Tool.Function("getCurrentWeather",
				"Find the current weather conditions, forecasts, and temperatures for a location, like a city or state.",
				ModelOptionsUtils.jsonToMap("""
						{
							"type": "object",
							"properties": {
								"location": {
									"type": "string",
									"description": "The city and state e.g. San Francisco, CA"
								},
								"unit": {
									"type": "string",
									"enum": ["C", "F"]
								}
							},
							"required": ["location", "unit"]
						}
						""")));

		List<Message> messages = new ArrayList<>(List.of(message));

		OllamaApi.ChatRequest chatCompletionRequest = OllamaApi.ChatRequest.builder(MODEL)
			.withMessages(messages)
			.withTools(List.of(functionTool))
			.build();

		ChatResponse chatCompletion = ollamaApi.chat(chatCompletionRequest);

		assertThat(chatCompletion).isNotNull();
		assertThat(chatCompletion.message()).isNotNull();

		Message responseMessage = chatCompletion.message();

		assertThat(responseMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		// Check if the model wanted to call a function

		// extend conversation with assistant's reply.
		messages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();
			if ("getCurrentWeather".equals(functionName)) {
				Map<String, Object> responseMap = toolCall.function().arguments();
				MockWeatherService.Request weatherRequest = ModelOptionsUtils.mapToClass(responseMap,
						MockWeatherService.Request.class);

				MockWeatherService.Response weatherResponse = this.weatherService.apply(weatherRequest);

				// extend conversation with function response.
				messages.add(Message.builder(Role.TOOL)
					.withContent("" + weatherResponse.temp() + weatherRequest.unit())
					.build());
			}
		}

		var functionResponseRequest = OllamaApi.ChatRequest.builder(MODEL).withMessages(messages).build();

		ChatResponse chatCompletion2 = ollamaApi.chat(functionResponseRequest);

		logger.info("Final response: " + chatCompletion2);

		assertThat(chatCompletion2).isNotNull();

		assertThat(chatCompletion2.message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(chatCompletion2.message().content()).contains("San Francisco").contains("30");
		assertThat(chatCompletion2.message().content()).contains("Tokyo").contains("10");
		assertThat(chatCompletion2.message().content()).contains("Paris").contains("15");
	}

}
