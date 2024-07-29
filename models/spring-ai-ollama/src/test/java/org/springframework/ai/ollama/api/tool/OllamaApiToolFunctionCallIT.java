/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.ollama.api.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;
import org.springframework.ai.ollama.api.OllamaModel;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

/**
 * @author Christian Tzolov
 */
@Disabled("For manual smoke testing only.")
@Testcontainers
public class OllamaApiToolFunctionCallIT {

	private static final String MODEL = OllamaModel.MISTRAL.getName();

	private static final Logger logger = LoggerFactory.getLogger(OllamaApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	@Container
	static OllamaContainer ollamaContainer = new OllamaContainer("ollama/ollama:0.2.8");

	static String baseUrl = "http://localhost:11434";

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL);
		logger.info(MODEL + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	@SuppressWarnings("null")
	@Test
	public void toolFunctionCall() {

		OllamaApi completionApi = new OllamaApi(baseUrl);

		// Step 1: send the conversation and available functions to the model
		var message = Message.builder(Role.USER)
			// .withContent("What's the weather like in San Francisco, Tokyo, and Paris?
			// Perform multiple function calls for each location.")
			.withContent("What's the weather like in San Francisco, Tokyo, and Paris?")
			.build();

		var functionTool = new OllamaApi.ChatRequest.Tool(new OllamaApi.ChatRequest.Tool.Function("getCurrentWeather",
				"Get the weather in location. Return temperature in Celsius.", ModelOptionsUtils.jsonToMap("""
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

		ChatResponse chatCompletion = completionApi.chat(chatCompletionRequest);

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

				MockWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

				// extend conversation with function response.
				messages.add(Message.builder(Role.TOOL)
					.withContent("" + weatherResponse.temp() + weatherRequest.unit())
					.build());
			}
		}

		var functionResponseRequest = OllamaApi.ChatRequest.builder(MODEL).withMessages(messages).build();

		ChatResponse chatCompletion2 = completionApi.chat(functionResponseRequest);

		logger.info("Final response: " + chatCompletion2);

		assertThat(chatCompletion2).isNotNull();

		assertThat(chatCompletion2.message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(chatCompletion2.message().content()).contains("San Francisco").contains("30");
		assertThat(chatCompletion2.message().content()).contains("Tokyo").contains("10");
		assertThat(chatCompletion2.message().content()).contains("Paris").contains("15");
	}

}