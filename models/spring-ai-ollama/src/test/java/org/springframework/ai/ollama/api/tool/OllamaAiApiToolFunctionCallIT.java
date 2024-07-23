/*
 * Copyright 2024 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapted from OpenAiApiToolFunctionCallIT in the OpenAI module (by Christian Tzolov).
 *
 * @author Thomas Vitale
 */
@Disabled("For manual smoke testing only.")
@Testcontainers
public class OllamaAiApiToolFunctionCallIT {

	private static final String MODEL = OllamaModel.MISTRAL.getName();

	private static final Logger logger = LoggerFactory.getLogger(OllamaAiApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	@Container
	static OllamaContainer ollamaContainer = new OllamaContainer("ollama/ollama:0.2.8");

	static OllamaApi ollamaApi;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL + " ' model ... can take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL);
		logger.info("orca-mini pulling competed!");

		ollamaApi = new OllamaApi("http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434));
	}

	@Test
	public void toolFunctionCall() {
		// Step 1: Send the question and the list of available functions to the model.
		var message = OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
			.withContent("What's the weather like in San Francisco, Tokyo, and Paris?")
			.build();

		var functionTool = new OllamaApi.Tool(OllamaApi.Tool.Type.FUNCTION,
				new OllamaApi.Tool.ToolFunction("Get the weather in location. Return temperature in Celsius.",
						"getCurrentWeather", ModelOptionsUtils.jsonToMap("""
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

		List<OllamaApi.Message> messages = new ArrayList<>(List.of(message));

		OllamaApi.ChatRequest chatRequest = OllamaApi.ChatRequest.builder(MODEL)
			.withMessages(messages)
			.withTools(List.of(functionTool))
			.withOptions(OllamaOptions.create().withTemperature(0.0f))
			.build();

		OllamaApi.ChatResponse chatResponse = ollamaApi.chat(chatRequest);

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.message()).isNotNull();

		OllamaApi.Message responseMessage = chatResponse.message();

		assertThat(responseMessage.role()).isEqualTo(OllamaApi.Message.Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		// Step 2a. Extend the conversation with the previous model response.
		messages.add(responseMessage);

		// Step 2b. For each tool call, execute the function and add response to the
		// conversation history.
		for (OllamaApi.Message.ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();
			if ("getCurrentWeather".equals(functionName)) {
				MockWeatherService.Request weatherRequest = fromMap(toolCall.function().arguments(),
						MockWeatherService.Request.class);

				MockWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

				// extend conversation with function response.
				messages.add(OllamaApi.Message.builder(OllamaApi.Message.Role.TOOL)
					.withContent("" + weatherResponse.temp() + weatherRequest.unit())
					.build());
			}
		}

		// Step 2c. Call the model again with the function execution results, aiming to
		// receive the final answer to the original question.

		OllamaApi.ChatRequest functionResponseRequest = OllamaApi.ChatRequest.builder(MODEL)
			.withMessages(messages)
			.withOptions(OllamaOptions.create().withTemperature(0.0f))
			.build();

		OllamaApi.ChatResponse chatResponse2 = ollamaApi.chat(functionResponseRequest);

		logger.info("Final response: \n" + chatResponse2);

		assertThat(chatResponse2).isNotNull();
		assertThat(chatResponse2.message()).isNotNull();

		assertThat(chatResponse2.message().role()).isEqualTo(OllamaApi.Message.Role.ASSISTANT);
		assertThat(chatResponse2.message().content()).contains("San Francisco").containsAnyOf("30.0°C", "30°C");
		assertThat(chatResponse2.message().content()).contains("Tokyo").containsAnyOf("10.0°C", "10°C");
		assertThat(chatResponse2.message().content()).contains("Paris").containsAnyOf("15.0°C", "15°C");
	}

	private static <T> T fromMap(Map<String, Object> map, Class<T> targetClass) {
		return new ObjectMapper().convertValue(map, targetClass);
	}

}