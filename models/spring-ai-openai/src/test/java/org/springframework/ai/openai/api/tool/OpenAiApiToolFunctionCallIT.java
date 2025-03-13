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

package org.springframework.ai.openai.api.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Based on the OpenAI Function Calling tutorial:
 * https://platform.openai.com/docs/guides/function-calling/parallel-function-calling
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiApiToolFunctionCallIT {

	private final Logger logger = LoggerFactory.getLogger(OpenAiApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	OpenAiApi completionApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();

	private static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("null")
	@Test
	public void toolFunctionCall() {

		// Step 1: send the conversation and available functions to the model
		var message = new ChatCompletionMessage("What's the weather like in San Francisco, Tokyo, and Paris?",
				Role.USER);

		var functionTool = new OpenAiApi.FunctionTool(OpenAiApi.FunctionTool.Type.FUNCTION,
				new OpenAiApi.FunctionTool.Function("Get the weather in location. Return temperature in Celsius.",
						"getCurrentWeather", ModelOptionsUtils.jsonToMap("""
								{
									"type": "object",
									"properties": {
										"location": {
											"type": "string",
											"description": "The city and state e.g. San Francisco, CA"
										},
										"lat": {
											"type": "number",
											"description": "The city latitude"
										},
										"lon": {
											"type": "number",
											"description": "The city longitude"
										},
										"unit": {
											"type": "string",
											"enum": ["C", "F"]
										}
									},
									"required": ["location", "lat", "lon", "unit"]
								}
								"""), null));

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(messages, "gpt-4o",
				List.of(functionTool), ToolChoiceBuilder.AUTO);
		// List.of(functionTool), ToolChoiceBuilder.FUNCTION("getCurrentWeather"));

		ResponseEntity<ChatCompletion> chatCompletion = this.completionApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(chatCompletion.getBody()).isNotNull();
		assertThat(chatCompletion.getBody().choices()).isNotEmpty();

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		// Check if the model wanted to call a function
		assertThat(responseMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		// extend conversation with assistant's reply.
		messages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();
			if ("getCurrentWeather".equals(functionName)) {
				MockWeatherService.Request weatherRequest = fromJson(toolCall.function().arguments(),
						MockWeatherService.Request.class);

				MockWeatherService.Response weatherResponse = this.weatherService.apply(weatherRequest);

				// extend conversation with function response.
				messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(), Role.TOOL,
						functionName, toolCall.id(), null, null, null, null));
			}
		}

		var functionResponseRequest = new ChatCompletionRequest(messages, "gpt-4o", 0.5);

		ResponseEntity<ChatCompletion> chatCompletion2 = this.completionApi
			.chatCompletionEntity(functionResponseRequest);

		logger.info("Final response: " + chatCompletion2.getBody());

		assertThat(chatCompletion2.getBody().choices()).isNotEmpty();

		assertThat(chatCompletion2.getBody().choices().get(0).message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("San Francisco")
			.containsAnyOf("30.0°C", "30°C");
		assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("Tokyo")
			.containsAnyOf("10.0°C", "10°C");
		assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("Paris")
			.containsAnyOf("15.0°C", "15°C");
	}

}
