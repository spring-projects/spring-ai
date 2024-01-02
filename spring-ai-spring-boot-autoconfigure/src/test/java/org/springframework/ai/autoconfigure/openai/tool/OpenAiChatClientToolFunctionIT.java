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

package org.springframework.ai.autoconfigure.openai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.openai.tool.FakeWeatherService.Request;
import org.springframework.ai.autoconfigure.openai.tool.FakeWeatherService.Response;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.model.AbstractToolFunctionCallback;
import org.springframework.ai.model.ToolFunctionCallback;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.client.OpenAiChatClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.ToolMessage;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiChatClientToolFunctionIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class));

	private static Map<String, ToolFunctionCallback> tooFunctionCallbacks = Map.of("getCurrentWeather",

			new AbstractToolFunctionCallback<FakeWeatherService.Request, FakeWeatherService.Response>(
					"getCurrentWeather", "Get the weather in location", FakeWeatherService.Request.class) {

				private final FakeWeatherService weatherService = new FakeWeatherService();

				@Override
				public Response doCall(Request request) {
					return weatherService.apply(request);
				}

				@Override
				public String doResponseToString(Response response) {
					return "" + response.temp() + response.unit();
				}

			});

	@Test
	void functionCallTest() {
		contextRunner
			.withPropertyValues("spring.ai.openai.chat.options.model=gpt-4-1106-preview",
					"spring.ai.openai.chat.options.tools[0].function.name=getCurrentWeather",
					"spring.ai.openai.chat.options.tools[0].function.description=Get the weather in location",
					"spring.ai.openai.chat.options.tools[0].function.jsonSchema=" + """
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
							""")
			.run(context -> {

				OpenAiChatClient chatClient = context.getBean(OpenAiChatClient.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Tokyo, and Paris?");

				List<Message> messages = new ArrayList<>(List.of(userMessage));

				ChatResponse response1 = chatClient.generate(new Prompt(messages));

				var generation = response1.getGeneration();

				if (generation.getProperties().containsKey("tool_calls")) {

					// Extend conversation with assistant's reply.
					messages.add(generation);

					// Send the info for each function call and function response to the
					// model.
					List<ToolCall> toolCalls = (List<ToolCall>) generation.getProperties().get("tool_calls");

					for (ToolCall toolCall : toolCalls) {

						var functionName = toolCall.function().name();

						ToolFunctionCallback functionCallBack = tooFunctionCallbacks.get(functionName);

						String functionArguments = toolCall.function().arguments();
						System.out.println("functionArguments: " + functionArguments);
						String functionResponseContent = functionCallBack.call(functionArguments);

						var toolMessage = new ToolMessage(functionResponseContent,
								Map.of("tool_call_id", toolCall.id()));
						messages.add(toolMessage);
					}
				}

				ChatResponse response2 = chatClient.generate(new Prompt(messages));

				System.out.println(response2.getGeneration().getContent());

				assertThat(response2.getGeneration().getContent()).contains("30.0", "10.0", "15.0");
			});
	}

}
