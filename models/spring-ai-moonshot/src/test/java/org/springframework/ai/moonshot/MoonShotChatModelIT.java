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
package org.springframework.ai.moonshot;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.moonshot.api.MockWeatherService;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
public class MoonShotChatModelIT {

	@Autowired
	private MoonshotChatModel chatModel;

	private static final MoonshotApi.FunctionTool FUNCTION_TOOL = new MoonshotApi.FunctionTool(
			MoonshotApi.FunctionTool.Type.FUNCTION, new MoonshotApi.FunctionTool.Function(
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

	@Test
	public void toolFunctionCall() {
		var promptOptions = MoonshotChatOptions.builder()
			.withModel(MoonshotApi.ChatModel.MOONSHOT_V1_8K.getValue())
			.withTools(Arrays.asList(FUNCTION_TOOL))
			.withFunctionCallbacks(List.of(FunctionCallback.builder()
				.function("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput());
		assertThat(chatResponse.getResult().getOutput().getText()).contains("San Francisco");
		assertThat(chatResponse.getResult().getOutput().getText()).contains("30.0");
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isLessThan(450).isGreaterThan(280);
	}

	@Test
	public void testStreamFunctionCall() {
		var promptOptions = MoonshotChatOptions.builder()
			.withModel(MoonshotApi.ChatModel.MOONSHOT_V1_8K.getValue())
			.withTools(Arrays.asList(FUNCTION_TOOL))
			.withFunctionCallbacks(List.of(FunctionCallback.builder()
				.function("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		Flux<ChatResponse> chatResponse = this.chatModel.stream(prompt);
		String content = chatResponse.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		assertThat(content).contains("San Francisco");
		assertThat(content).contains("30.0");
	}

	@Test
	public void testStreamFunctionCallUsage() {
		var promptOptions = MoonshotChatOptions.builder()
			.withModel(MoonshotApi.ChatModel.MOONSHOT_V1_8K.getValue())
			.withTools(Arrays.asList(FUNCTION_TOOL))
			.withFunctionCallbacks(List.of(FunctionCallback.builder()
				.function("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		ChatResponse chatResponse = this.chatModel.stream(prompt).blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isLessThan(450).isGreaterThan(280);
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public MoonshotApi moonshotApi() {
			return new MoonshotApi(getApiKey());
		}

		private String getApiKey() {
			String apiKey = System.getenv("MOONSHOT_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key.  Put it in an environment variable under the name MOONSHOT_API_KEY");
			}
			return apiKey;
		}

		@Bean
		public MoonshotChatModel moonshotChatModel(MoonshotApi api) {
			return new MoonshotChatModel(api);
		}

	}

}
