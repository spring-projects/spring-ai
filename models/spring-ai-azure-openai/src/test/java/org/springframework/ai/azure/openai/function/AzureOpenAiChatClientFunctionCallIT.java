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
package org.springframework.ai.azure.openai.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.azure.openai.AzureOpenAiChatClient;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureOpenAiChatClientFunctionCallIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
class AzureOpenAiChatClientFunctionCallIT {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatClientFunctionCallIT.class);

	@Autowired
	private String selectedModel;

	@Autowired
	private AzureOpenAiChatClient chatClient;

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, in Tokyo, and in Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.withDeploymentName(selectedModel)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the current weather in a given location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		ChatResponse response = chatClient.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("10.0", "10");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.withDeploymentName(selectedModel)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the current weather in a given location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		Flux<ChatResponse> response = chatClient.stream(new Prompt(messages, promptOptions));

		final var counter = new AtomicInteger();
		String content = response.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(counter.get()).isGreaterThan(2);
		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@Test
	void functionCallWithoutCompleteRoundTrip() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		final var spyingMockWeatherService = new SpyingMockWeatherService();
		var promptOptions = AzureOpenAiChatOptions.builder()
			.withDeploymentName(selectedModel)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(spyingMockWeatherService)
				.withName("getCurrentWeather")
				.withDescription("Get the current weather in a given location")
				.build()))
			.build();

		ChatResponse response = chatClient.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);
		final var interceptedRequest = spyingMockWeatherService.getInterceptedRequest();
		assertThat(interceptedRequest.location()).containsIgnoringCase("San Francisco");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClient openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.buildClient();
		}

		@Bean
		public AzureOpenAiChatClient azureOpenAiChatClient(OpenAIClient openAIClient, String selectedModel) {
			return new AzureOpenAiChatClient(openAIClient,
					AzureOpenAiChatOptions.builder().withDeploymentName(selectedModel).withMaxTokens(500).build());
		}

		@Bean
		public String selectedModel() {
			return Optional.ofNullable(System.getenv("AZURE_OPENAI_MODEL")).orElse("gpt-4-0125-preview");
		}

	}

}
