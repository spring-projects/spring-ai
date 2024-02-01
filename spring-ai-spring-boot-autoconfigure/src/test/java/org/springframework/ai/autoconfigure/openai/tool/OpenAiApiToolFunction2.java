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

package org.springframework.ai.autoconfigure.openai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool.Type;
import org.springframework.http.ResponseEntity;

/**
 * Based on the OpenAI Function Calling tutorial:
 * https://platform.openai.com/docs/guides/function-calling/parallel-function-calling
 *
 * @author Christian Tzolov
 */
public class OpenAiApiToolFunction2 {

	public static class Pair2 {

		private final String key;

		private final ChatCompletionMessage value;

		public Pair2(String key, ChatCompletionMessage value) {
			this.key = key;
			this.value = value;
		}

		public String key() {
			return key;
		}

		public ChatCompletionMessage value() {
			return value;
		}

	}

	public static void main(String[] args) {

		var weatherService = new FakeWeatherService();

		OpenAiApi completionApi = new OpenAiApi(System.getenv("OPENAI_API_KEY"));

		// Step 1: send the conversation and available functions to the model
		var message = new ChatCompletionMessage("What's the weather like in San Francisco, Tokyo, and Paris?",
				Role.USER);

		var functionTool = new OpenAiApi.FunctionTool(Type.FUNCTION, new OpenAiApi.FunctionTool.Function(
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

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(messages, "gpt-4-1106-preview", 0.0f,
				null, null, 1, 0.0f, null, null, null, true, 0.8f, null, List.of(functionTool), null, null);

		Flux<ChatCompletionChunk> completionChunks = completionApi.chatCompletionStream(chatCompletionRequest);

		///
		final AtomicReference<String> keyHolder = new AtomicReference<>();

		Flux<List<Pair2>> b1 = completionChunks.map(chunk -> {
			ChunkChoice choice = chunk.choices().iterator().next();

			String key = "" + choice.index();

			if (choice.delta().toolCalls() != null) {
				ToolCall toolCall = choice.delta().toolCalls().iterator().next();
				if (toolCall.id() != null) {
					keyHolder.set(toolCall.id());
				}
			}

			if (keyHolder.get() != null) {
				key = keyHolder.get();
			}

			return new Pair2(key, choice.delta());
		}).groupBy(new Function<Pair2, String>() {
			@Override
			public String apply(Pair2 pair) {
				return pair.key();
			}
		}).flatMap(g -> g.collectList());

		Flux<ChatCompletionMessage> b11 = b1.map(pairs -> {
			if (pairs.size() == 1) {
				Pair2 pair = pairs.get(0);
				ChatCompletionMessage message1 = pair.value();
				return message1;
			}
			else if (pairs.size() > 1) {
				String functionName = "";
				String toolId = "";
				String arguments = "";
				for (Pair2 pair : pairs) {
					ChatCompletionMessage message1 = pair.value();
					if (message1.toolCalls() != null) {
						for (ToolCall toolCall : message1.toolCalls()) {

							if (toolCall.function().name() != null) {
								functionName = toolCall.function().name();
							}
							if (toolCall.id() != null) {
								toolId = toolCall.id();
							}
							if (toolCall.function().arguments() != null) {
								arguments = arguments + toolCall.function().arguments();
							}
						}
					}
				}
				ChatCompletionFunction function = new ChatCompletionFunction(functionName, arguments);

				return new ChatCompletionMessage(null, Role.ASSISTANT, null, null,
						List.of(new ToolCall(toolId, "function", function)));
			}

			return new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null);
		});

		List<ChatCompletionMessage> b2 = b11.collectList().block();

		completionChunks.map(chunk -> {
			String chunkId = chunk.id();
			List<ChunkChoice> chunks = chunk.choices().stream().map(choice -> {

				if (choice.delta().toolCalls() != null) {
					// extend conversation with assistant's reply.
					messages.add(choice.delta());

					// Send the info for each function call and function response to the
					// model.
					for (ToolCall toolCall : choice.delta().toolCalls()) {
						var functionName = toolCall.function().name();
						if ("getCurrentWeather".equals(functionName)) {
							FakeWeatherService.Request weatherRequest = fromJson(toolCall.function().arguments(),
									FakeWeatherService.Request.class);

							FakeWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

							// extend conversation with function response.
							messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(),
									Role.TOOL, null, toolCall.id(), null));
						}
					}

					ChatCompletionRequest functionResponseRequest = new ChatCompletionRequest(messages,
							"gpt-4-1106-preview", 0.8f);

					ResponseEntity<ChatCompletion> chatCompletion2 = completionApi
						.chatCompletionEntity(functionResponseRequest);

					System.out.println(chatCompletion2.getBody());
				}
				return choice;

			}).toList();
			return chunks;
		}).collectList().block();

		ResponseEntity<ChatCompletion> chatCompletion = completionApi.chatCompletionEntity(chatCompletionRequest);

		ChatCompletionMessage responseMessage = chatCompletion.getBody().choices().get(0).message();

		// Check if the model wanted to call a function
		if (responseMessage.toolCalls() != null) {

			// extend conversation with assistant's reply.
			messages.add(responseMessage);

			// Send the info for each function call and function response to the model.
			for (ToolCall toolCall : responseMessage.toolCalls()) {
				var functionName = toolCall.function().name();
				if ("getCurrentWeather".equals(functionName)) {
					FakeWeatherService.Request weatherRequest = fromJson(toolCall.function().arguments(),
							FakeWeatherService.Request.class);

					FakeWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

					// extend conversation with function response.
					messages.add(new ChatCompletionMessage("" + weatherResponse.temp() + weatherRequest.unit(),
							Role.TOOL, null, toolCall.id(), null));
				}
			}

			ChatCompletionRequest functionResponseRequest = new ChatCompletionRequest(messages, "gpt-4-1106-preview",
					0.8f);

			ResponseEntity<ChatCompletion> chatCompletion2 = completionApi
				.chatCompletionEntity(functionResponseRequest);

			System.out.println(chatCompletion2.getBody());
		}

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