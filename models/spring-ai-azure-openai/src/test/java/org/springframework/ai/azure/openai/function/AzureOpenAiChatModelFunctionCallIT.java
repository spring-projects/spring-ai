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

package org.springframework.ai.azure.openai.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletionStreamOptions;
import com.azure.core.credential.AzureKeyCredential;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.RequiresAzureCredentials;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureOpenAiChatModelFunctionCallIT.TestConfiguration.class)
@RequiresAzureCredentials
class AzureOpenAiChatModelFunctionCallIT {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatModelFunctionCallIT.class);

	@Autowired
	private String selectedModel;

	@Autowired
	private AzureOpenAiChatModel chatModel;

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, in Tokyo, and in Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(this.selectedModel)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isGreaterThan(600).isLessThan(800);
	}

	@Test
	void functionCallSequentialTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? If the weather is above 25 degrees, please check the weather in Tokyo and Paris.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(this.selectedModel)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(this.selectedModel)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		final var counter = new AtomicInteger();
		String content = response.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(counter.get()).withFailMessage("The response should be chunked in more than 30 messages")
			.isGreaterThan(30);

		assertThat(content).contains("30", "10", "15");

	}

	@Test
	void streamFunctionCallUsageTest() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		ChatCompletionStreamOptions streamOptions = new ChatCompletionStreamOptions();
		streamOptions.setIncludeUsage(true);

		var promptOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(this.selectedModel)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.streamOptions(streamOptions)
			.build();

		List<ChatResponse> responses = this.chatModel.stream(new Prompt(messages, promptOptions)).collectList().block();

		assertThat(responses).isNotEmpty();

		ChatResponse finalResponse = responses.get(responses.size() - 2);

		logger.info("Final Response: {}", finalResponse);

		assertThat(finalResponse.getMetadata()).isNotNull();
		assertThat(finalResponse.getMetadata().getUsage()).isNotNull();

		assertThat(finalResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(600).isLessThan(800);

	}

	@Test
	void functionCallSequentialAndStreamTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? If the weather is above 25 degrees, please check the weather in Tokyo and Paris.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AzureOpenAiChatOptions.builder()
			.deploymentName(this.selectedModel)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		var response = this.chatModel.stream(new Prompt(messages, promptOptions));

		final var counter = new AtomicInteger();
		String content = response.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(Objects::nonNull)
			.collect(Collectors.joining());

		logger.info("Response: {}", response);

		assertThat(content).contains("30", "10", "15");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		public static String getDeploymentName() {
			String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
			if (StringUtils.hasText(deploymentName)) {
				return deploymentName;
			}
			else {
				return "gpt-4o";
			}
		}

		@Bean
		public OpenAIClientBuilder openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"));
		}

		@Bean
		public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClient, String selectedModel) {
			return AzureOpenAiChatModel.builder()
				.openAIClientBuilder(openAIClient)
				.defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(selectedModel).maxTokens(500).build())
				.build();
		}

		@Bean
		public String selectedModel() {
			return Optional.ofNullable(System.getenv("AZURE_OPENAI_MODEL")).orElse(getDeploymentName());
		}

	}

}
