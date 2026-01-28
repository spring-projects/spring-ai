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

package org.springframework.ai.huggingface.api.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.HuggingfaceApi.ChatRequest;
import org.springframework.ai.huggingface.api.HuggingfaceApi.ChatResponse;
import org.springframework.ai.huggingface.api.HuggingfaceApi.Message;
import org.springframework.ai.huggingface.api.HuggingfaceApi.ToolCall;
import org.springframework.ai.model.ModelOptionsUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Low-level API integration tests for HuggingFace tool/function calling.
 *
 * <p>
 * Note: Function calling is only supported by specific models and providers on
 * HuggingFace Inference API. This test uses meta-llama/Llama-3.2-3B-Instruct (3B
 * parameters) with the 'together' provider specified using the :provider suffix notation.
 * The model supports function calling through multiple providers (novita, hyperbolic,
 * together). To specify a provider, append :provider-name to the model ID (e.g.,
 * "model:together", "model:fastest", "model:cheapest").
 * </p>
 *
 * @author Myeongdeok Kang
 * @see <a href=
 * "https://huggingface.co/docs/inference-providers/guides/function-calling">HuggingFace
 * Function Calling Guide</a>
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
public class HuggingfaceApiToolFunctionCallIT {

	private final Logger logger = LoggerFactory.getLogger(HuggingfaceApiToolFunctionCallIT.class);

	MockWeatherService weatherService = new MockWeatherService();

	HuggingfaceApi huggingfaceApi = HuggingfaceApi.builder().apiKey(System.getenv("HUGGINGFACE_API_KEY")).build();

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
		// Reset the weather service call history before the test
		this.weatherService.reset();

		// Step 1: send the conversation and available functions to the model
		var message = new Message("user", "What's the weather like in San Francisco, Tokyo, and Paris?");

		var functionTool = new HuggingfaceApi.FunctionTool(HuggingfaceApi.FunctionTool.Type.FUNCTION,
				new HuggingfaceApi.FunctionTool.Function("Get the weather in location. Return temperature in Celsius.",
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
								""")));

		List<Message> messages = new ArrayList<>(List.of(message));

		// Specify the 'together' provider using :provider suffix notation
		ChatRequest chatRequest = new ChatRequest("meta-llama/Llama-3.2-3B-Instruct:together", messages,
				List.of(functionTool), "auto");

		ChatResponse chatResponse = this.huggingfaceApi.chat(chatRequest);

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.choices()).isNotEmpty();

		Message responseMessage = chatResponse.choices().get(0).message();

		// Check if the model wanted to call a function
		assertThat(responseMessage.role()).isEqualTo("assistant");
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
				messages.add(new Message("" + weatherResponse.temp() + weatherRequest.unit(), "tool", functionName,
						toolCall.id()));
			}
		}

		// Use the same provider for the follow-up request
		var functionResponseRequest = new ChatRequest("meta-llama/Llama-3.2-3B-Instruct:together", messages);

		ChatResponse chatResponse2 = this.huggingfaceApi.chat(functionResponseRequest);

		logger.info("Final response: " + chatResponse2);

		assertThat(chatResponse2.choices()).isNotEmpty();
		assertThat(chatResponse2.choices().get(0).message().role()).isEqualTo("assistant");
		assertThat(chatResponse2.choices().get(0).message().content()).isNotEmpty();

		// Verify that all three cities are mentioned in the response
		String finalContent = chatResponse2.choices().get(0).message().content();
		assertThat(finalContent).containsIgnoringCase("San Francisco");
		assertThat(finalContent).containsIgnoringCase("Tokyo");
		assertThat(finalContent).containsIgnoringCase("Paris");

		// Verify that the function was actually called 3 times (once for each city)
		assertThat(this.weatherService.getCallCount()).isEqualTo(3);

		// Verify the function was called with correct locations
		List<MockWeatherService.Request> callHistory = this.weatherService.getCallHistory();
		assertThat(callHistory).hasSize(3);

		List<String> locations = callHistory.stream().map(MockWeatherService.Request::location).toList();
		assertThat(locations).anyMatch(loc -> loc.contains("San Francisco"));
		assertThat(locations).anyMatch(loc -> loc.contains("Tokyo"));
		assertThat(locations).anyMatch(loc -> loc.contains("Paris"));

		// Verify all calls used Celsius unit
		assertThat(callHistory).allMatch(req -> req.unit() == MockWeatherService.Unit.C);

		// Verify lat/lon were provided for all calls
		assertThat(callHistory).allMatch(req -> req.lat() != null && req.lon() != null);
	}

}
