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

package org.springframework.ai.autoconfigure.mistralai.tool;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.autoconfigure.mistralai.MistralAiAutoConfiguration;
import org.springframework.ai.autoconfigure.mistralai.tool.WeatherServicePromptIT.MyWeatherService.Request;
import org.springframework.ai.autoconfigure.mistralai.tool.WeatherServicePromptIT.MyWeatherService.Response;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.MistralAiChatClient;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
public class WeatherServicePromptIT {

	private final Logger logger = LoggerFactory.getLogger(WeatherServicePromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.api-key=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, MistralAiAutoConfiguration.class));

	@Test
	void promptFunctionCall() {
		contextRunner
			.withPropertyValues("spring.ai.mistralai.chat.options.model=" + MistralAiApi.ChatModel.LARGE.getValue())
			.run(context -> {

				MistralAiChatClient chatClient = context.getBean(MistralAiChatClient.class);

				UserMessage userMessage = new UserMessage("What's the weather like in Paris?");
				// UserMessage userMessage = new UserMessage("What's the weather like in
				// San Francisco, Tokyo, and
				// Paris?");

				var promptOptions = MistralAiChatOptions.builder()
					.withToolChoice(ToolChoice.AUTO)
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MyWeatherService())
						.withName("CurrentWeatherService")
						.withDescription("Get the current weather in requested location")
						.build()))
					.build();

				ChatResponse response = chatClient.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15", "15.0");
				// assertThat(response.getResult().getOutput().getContent()).contains("30.0",
				// "10.0", "15.0");
			});
	}

	public static class MyWeatherService implements Function<Request, Response> {

		// @formatter:off
		public enum Unit { C, F }

		@JsonInclude(Include.NON_NULL)
		public record Request(
				@JsonProperty(required = true, value = "location") String location,
				@JsonProperty(required = true, value = "unit") Unit unit) {}

		public record Response(double temperature, Unit unit) {}
		// @formatter:on

		@Override
		public Response apply(Request request) {
			if (request.location().contains("Paris")) {
				return new Response(15, request.unit());
			}
			else if (request.location().contains("Tokyo")) {
				return new Response(10, request.unit());
			}
			else if (request.location().contains("San Francisco")) {
				return new Response(30, request.unit());
			}
			throw new IllegalArgumentException("Invalid request: " + request);
		}

	}

}