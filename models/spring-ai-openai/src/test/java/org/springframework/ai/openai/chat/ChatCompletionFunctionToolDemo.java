/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.openai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.openai.api.ChatCompletionRequestBuilder;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool.Type;
import org.springframework.ai.openai.client.ChatCompletionFunctionToolDemo.WeatherFunction.Request;
import org.springframework.ai.openai.client.ChatCompletionFunctionToolDemo.WeatherFunction.Response;
import org.springframework.http.ResponseEntity;

/**
 * Based on the OpenAI Function Calling tutorial:
 * https://platform.openai.com/docs/guides/function-calling/parallel-function-calling
 *
 * @author Christian Tzolov
 */
public class ChatCompletionFunctionToolDemo {

	public static class WeatherFunction implements Function<Request, Response> {

		private final String key;

		public WeatherFunction(String key) {
			this.key = key;
		}

		/**
		 * Weather Function request.
		 */
		public record Request(String location, double lat, double lon, Unit unit) {
		}

		/**
		 * Temperature units.
		 */
		public enum Unit {

			/**
			 * Celsius.
			 */
			c("metric"),
			/**
			 * Farenthide.
			 */
			f("imperial");

			/**
			 * Human readable unit name.
			 */
			public final String unitName;

			private Unit(String text) {
				this.unitName = text;
			}

		}

		/**
		 * Weather Function response.
		 */
		public record Response(double temp, double feels_like, double temp_min, double temp_max, int pressure,
				int humidity) {
		}

		@Override
		public Response apply(Request request) {

			if (request.unit == Unit.c) {
				return new Response(10, 15, 20, 2, 53, 45);
			}
			else {
				return new Response(50, 60, 70, 20, 53, 45);
			}
		}

	}

	public static void main(String[] args) {

		var weatherService = new WeatherFunction("bla");

		OpenAiApi completionApi = new OpenAiApi(System.getenv("OPENAI_API_KEY"));

		// Step 1: send the conversation and available functions to the model
		var message = new ChatCompletionMessage("What's the weather like in San Francisco, Tokyo, and Paris?",
				Role.user);

		var functionTool = new OpenAiApi.FunctionTool(Type.function, new OpenAiApi.FunctionTool.Function(
				"Get the weather in location", "getCurrentWeather", OpenAiApi.parseJson("""
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
									"enum": ["c", "f"]
								}
							},
							"required": ["location", "lat", "lon", "unit"]
						}
						""")));

		List<ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		// var chatCompletionRequest = ChatCompletionRequest
		// .from(OpenAiOptionsBuilder.builder()
		// .withModel("gpt-4-1106-preview")
		// .withTools(List.of(functionTool))
		// .withToolChoice(null) // null == auto
		// .build())
		// .withMessages(messages);

		var chatCompletionRequest = ChatCompletionRequestBuilder.builder()
			.withMessages(messages)
			.withModel("gpt-4-1106-preview")
			.withTools(List.of(functionTool))
			.withToolChoice(null) // null == auto
			.build();

		ResponseEntity<ChatCompletion> chatCompletion = completionApi.chatCompletionEntity(chatCompletionRequest);

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		// Step 2: check if the model wanted to call a function
		if (responseMessage.toolCalls() != null) {
			// Step 3: call the function
			// Note: the JSON response may not always be valid; be sure to handle errors

			// extend conversation with assistant's reply.
			messages.add(responseMessage);

			// Step 4: send the info for each function call and function response to the
			// model.
			for (ToolCall toolCall : responseMessage.toolCalls()) {
				var functionName = toolCall.function().name();
				if ("getCurrentWeather".equals(functionName)) {
					WeatherFunction.Request weatherRequest = fromJson(toolCall.function().arguments(),
							WeatherFunction.Request.class);

					Response weatherResponse = weatherService.apply(weatherRequest);

					// extend conversation with function response.
					messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(),
							Role.tool, null, toolCall.id(), null));
				}
			}

			// var functionResponseRequest = ChatCompletionRequest
			// .from(OpenAiOptionsBuilder.builder()
			// .withModel("gpt-4-1106-preview")
			// .withTemperature(0.8f)
			// .build())
			// .withMessages(messages);

			var functionResponseRequest = ChatCompletionRequestBuilder.builder()
				.withMessages(messages)
				.withModel("gpt-4-1106-preview")
				.withTemperature(0.8f)
				.build();
			ResponseEntity<ChatCompletion> chatCompletion2 = completionApi
				.chatCompletionEntity(functionResponseRequest);

			System.out.println(chatCompletion2.getBody());
		}

	}

	public static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
