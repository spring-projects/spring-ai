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

package org.springframework.ai.cohere.api.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool.Type;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
public class CohereApiToolFunctionCallIT {

	static final String MISTRAL_AI_CHAT_MODEL = CohereApi.ChatModel.COMMAND_A_R7B.getValue();

	private final Logger logger = LoggerFactory.getLogger(CohereApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	CohereApi completionApi = CohereApi.builder().apiKey(System.getenv("COHERE_API_KEY")).build();

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

		var functionTool = new CohereApi.FunctionTool(Type.FUNCTION,
				new CohereApi.FunctionTool.Function(
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

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(messages, MISTRAL_AI_CHAT_MODEL,
				List.of(functionTool), ToolChoice.REQUIRED);

		System.out
			.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(chatCompletionRequest));

		ResponseEntity<ChatCompletion> response = this.completionApi.chatCompletionEntity(chatCompletionRequest);

		ChatCompletion chatCompletion = response.getBody();

		assertThat(chatCompletion).isNotNull();
		assertThat(chatCompletion.message()).isNotNull();

		ChatCompletionMessage responseMessage = new ChatCompletionMessage(chatCompletion.message().content(),
				chatCompletion.message().role(), chatCompletion.message().toolPlan(),
				chatCompletion.message().toolCalls(), chatCompletion.message().citations(), null);

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
							Role.TOOL, functionName, null, responseMessage.citations(), toolCall.id()));
				}
			}

			var functionResponseRequest = new ChatCompletionRequest(messages, MISTRAL_AI_CHAT_MODEL, 0.8);

			ResponseEntity<ChatCompletion> result2 = this.completionApi.chatCompletionEntity(functionResponseRequest);

			chatCompletion = result2.getBody();

			logger.info("Final response: {}", chatCompletion);

			assertThat(chatCompletion.message().content()).isNotEmpty();

			var messageContent = chatCompletion.message().content().get(0);

			assertThat(chatCompletion.message().role()).isEqualTo(Role.ASSISTANT);
			assertThat(messageContent.text()).contains("San Francisco").containsAnyOf("30.0", "30");
			assertThat(messageContent.text()).contains("Tokyo").containsAnyOf("10.0", "10");
			assertThat(messageContent.text()).contains("Paris").containsAnyOf("15.0", "15");
		}

	}

}
