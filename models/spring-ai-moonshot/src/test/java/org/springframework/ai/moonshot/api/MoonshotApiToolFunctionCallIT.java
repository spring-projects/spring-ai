/*
 * Copyright 2023 - 2024 the original author or authors.
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

package org.springframework.ai.moonshot.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage.Role;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionRequest;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionRequest.ToolChoiceBuilder;
import org.springframework.ai.moonshot.api.MoonshotApi.FunctionTool;
import org.springframework.ai.moonshot.api.MoonshotApi.FunctionTool.Type;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
public class MoonshotApiToolFunctionCallIT {

	private final Logger logger = LoggerFactory.getLogger(MoonshotApiToolFunctionCallIT.class);

	private final MockWeatherService weatherService = new MockWeatherService();

	private final MoonshotApi moonshotApi = new MoonshotApi(System.getenv("MOONSHOT_API_KEY"));

	private static final FunctionTool FUNCTION_TOOL = new FunctionTool(Type.FUNCTION, new FunctionTool.Function(
			"Get the weather in location. Return temperature in 30°F or 30°C format.", "getCurrentWeather", """
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
					"""));

	@SuppressWarnings("null")
	@Test
	public void toolFunctionCall() {
		toolFunctionCall("What's the weather like in San Francisco? Return the temperature in Celsius.",
				"San Francisco");
	}

	@Test
	public void toolFunctionCallChinese() {
		toolFunctionCall("旧金山、东京和巴黎的气温怎么样? 返回摄氏度的温度", "旧金山");
	}

	private void toolFunctionCall(String userMessage, String cityName) {
		// Step 1: send the conversation and available functions to the model
		var message = new ChatCompletionMessage(userMessage, Role.USER);

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(messages,
				MoonshotApi.ChatModel.MOONSHOT_V1_8K.getValue(), List.of(FUNCTION_TOOL), ToolChoiceBuilder.AUTO);

		ResponseEntity<ChatCompletion> chatCompletion = moonshotApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(chatCompletion.getBody()).isNotNull();
		assertThat(chatCompletion.getBody().choices()).isNotEmpty();

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		assertThat(responseMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		messages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {
			var functionName = toolCall.function().name();
			if ("getCurrentWeather".equals(functionName)) {
				MockWeatherService.Request weatherRequest = fromJson(toolCall.function().arguments(),
						MockWeatherService.Request.class);

				MockWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

				// extend conversation with function response.
				messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(), Role.TOOL,
						functionName, toolCall.id(), null));
			}
		}

		var functionResponseRequest = new ChatCompletionRequest(messages,
				MoonshotApi.ChatModel.MOONSHOT_V1_8K.getValue(), 0.5F);

		ResponseEntity<ChatCompletion> chatCompletion2 = moonshotApi.chatCompletionEntity(functionResponseRequest);

		logger.info("Final response: " + chatCompletion2.getBody());

		assertThat(Objects.requireNonNull(chatCompletion2.getBody()).choices()).isNotEmpty();

		assertThat(chatCompletion2.getBody().choices().get(0).message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(chatCompletion2.getBody().choices().get(0).message().content()).contains(cityName)
			.containsAnyOf("30");
	}

	private static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}