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

package org.springframework.ai.model.mistralai.autoconfigure.tool;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration;
import org.springframework.ai.model.mistralai.autoconfigure.tool.WeatherServicePromptIT.MyWeatherService.Request;
import org.springframework.ai.model.mistralai.autoconfigure.tool.WeatherServicePromptIT.MyWeatherService.Response;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Issam El-atif
 * @since 0.8.1
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
public class WeatherServicePromptIT {

	private final Logger logger = LoggerFactory.getLogger(WeatherServicePromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.api-key=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class));

	@Test
	void promptFunctionCall() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mistralai.chat.options.model=" + MistralAiApi.ChatModel.MISTRAL_LARGE.getValue())
			.run(context -> {

				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);

				UserMessage userMessage = new UserMessage("What's the weather like in Paris? Use Celsius.");
				// UserMessage userMessage = new UserMessage("What's the weather like in
				// San Francisco, Tokyo, and
				// Paris?");

				var promptOptions = MistralAiChatOptions.builder()
					.toolChoice(ToolChoice.AUTO)
					.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeatherService", new MyWeatherService())
						.description("Get the current weather in requested location")
						.inputType(MyWeatherService.Request.class)
						.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).containsAnyOf("15", "15.0");
			});
	}

	@Test
	void functionCallWithPortableFunctionCallingOptions() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mistralai.chat.options.model=" + MistralAiApi.ChatModel.MISTRAL_LARGE.getValue())
			.run(context -> {

				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);

				UserMessage userMessage = new UserMessage("What's the weather like in Paris? Use Celsius.");

				ToolCallingChatOptions functionOptions = ToolCallingChatOptions.builder()
					.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeatherService", new MyWeatherService())
						.description("Get the current weather in requested location")
						.inputType(MyWeatherService.Request.class)
						.build()))

					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), functionOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).containsAnyOf("15", "15.0");
			});
	}

	public static class MyWeatherService implements Function<Request, Response> {

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

		// @formatter:off
		public enum Unit { C, F }

		@JsonInclude(Include.NON_NULL)
		public record Request(
				@JsonProperty(required = true, value = "location") String location,
				@JsonProperty(required = true, value = "unit") Unit unit) { }
		// @formatter:on

		public record Response(double temperature, Unit unit) {

		}

	}

}
