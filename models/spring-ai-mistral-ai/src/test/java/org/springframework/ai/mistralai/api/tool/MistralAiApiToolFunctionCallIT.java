/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.mistralai.api.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.FunctionTool.Type;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Ricken Bazolo
 * @author Jason Smith
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
public class MistralAiApiToolFunctionCallIT {

	static final String MISTRAL_AI_CHAT_MODEL = MistralAiApi.ChatModel.MISTRAL_LARGE.getValue();

	private final Logger logger = LoggerFactory.getLogger(MistralAiApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	MistralAiApi completionApi = MistralAiApi.builder().apiKey(System.getenv("MISTRAL_AI_API_KEY")).build();

	private static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	@SuppressWarnings("null")
	public void toolFunctionCall() throws JsonProcessingException {

		// Step 1: send the conversation and available functions to the model
		var message = new ChatCompletionMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Show the temperature in Celsius.",
				Role.USER);

		var functionTool = new MistralAiApi.FunctionTool(Type.FUNCTION,
				new MistralAiApi.FunctionTool.Function(
						"Get the weather in location. Return temperature in 30°F or 30°C format.", "getCurrentWeather",
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

		// Or you can use the
		// ModelOptionsUtils.getJsonSchema(FakeWeatherService.Request.class))) to
		// auto-generate the JSON schema like:
		// var functionTool = new MistralAiApi.FunctionTool(Type.FUNCTION, new
		// MistralAiApi.FunctionTool.Function(
		// "Get the weather in location. Return temperature in 30°F or 30°C format.",
		// "getCurrentWeather",
		// ModelOptionsUtils.getJsonSchema(MockWeatherService.Request.class)));

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(messages, MISTRAL_AI_CHAT_MODEL,
				List.of(functionTool), ToolChoice.AUTO);

		System.out
			.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(chatCompletionRequest));

		ResponseEntity<ChatCompletion> chatCompletion = this.completionApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(chatCompletion.getBody()).isNotNull();
		assertThat(chatCompletion.getBody().choices()).isNotEmpty();

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		assertThat(responseMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		// Check if the model wanted to call a function
		if (!ObjectUtils.isEmpty(responseMessage.toolCalls())) {

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
					messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(),
							Role.TOOL, functionName, null, toolCall.id()));
				}
			}

			var functionResponseRequest = new ChatCompletionRequest(messages, MISTRAL_AI_CHAT_MODEL, 0.8);

			ResponseEntity<ChatCompletion> chatCompletion2 = this.completionApi
				.chatCompletionEntity(functionResponseRequest);

			logger.info("Final response: " + chatCompletion2.getBody());

			assertThat(chatCompletion2.getBody().choices()).isNotEmpty();

			assertThat(chatCompletion2.getBody().choices().get(0).message().role()).isEqualTo(Role.ASSISTANT);
			assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("San Francisco")
				.containsAnyOf("30.0", "30");
			assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("Tokyo")
				.containsAnyOf("10.0", "10");
			assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains("Paris")
				.containsAnyOf("15.0", "15");
		}

	}

}
