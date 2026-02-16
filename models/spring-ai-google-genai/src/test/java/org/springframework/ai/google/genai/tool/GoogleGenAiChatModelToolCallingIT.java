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

package org.springframework.ai.google.genai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
public class GoogleGenAiChatModelToolCallingIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiChatModelToolCallingIT.class);

	@Autowired
	private GoogleGenAiChatModel chatModel;

	@Test
	public void functionCallExplicitOpenApiSchema() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Paris and in Tokyo? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		String openApiSchema = """
				{
					"type": "OBJECT",
					"properties": {
						"location": {
						"type": "STRING",
						"description": "The city and state e.g. San Francisco, CA"
					},
						"unit" : {
						"type" : "STRING",
						"enum" : [ "C", "F" ],
						"description" : "Temperature unit"
						}
					},
					"required": ["location", "unit"]
					}
					""";

		var promptOptions = GoogleGenAiChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputSchema(openApiSchema)
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	public void includeThoughtsInResponseWithToolCalls() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in Tokyo, Japan? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.includeThoughts(true)
			.internalToolExecutionEnabled(false)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		List<Generation> generations = this.chatModel.call(new Prompt(messages, promptOptions)).getResults();

		String responseString = generations.stream()
			.map(Generation::getOutput)
			.map(AbstractMessage::getText)
			.collect(Collectors.joining("\n\n"));

		logger.info("Response: {}", responseString);

		assertThat(generations).hasSize(1);

		AssistantMessage response = generations.get(0).getOutput();
		assertThat(response.isThought()).isTrue();
		assertThat(response.getText()).isNotBlank();
		assertThat(response.getToolCalls()).containsAnyOf(
				// JSON serialization of Map doesn't guarantee key order,
				// so check both possible orderings to avoid flaky tests
				new AssistantMessage.ToolCall("", "function", "getCurrentWeather",
						"{\"location\":\"Tokyo, Japan\",\"unit\":\"C\"}"),
				new AssistantMessage.ToolCall("", "function", "getCurrentWeather",
						"{\"unit\":\"C\",\"location\":\"Tokyo, Japan\"}"));
	}

	@Test
	public void functionCallTestInferredOpenApiSchema() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Paris and in Tokyo? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.toolCallbacks(List.of(
					FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
						.description("Get the current weather in a given location.")
						.inputType(MockWeatherService.Request.class)
						.build(),
					FunctionToolCallback.builder("get_payment_status", new PaymentStatus())
						.description(
								"Retrieves the payment status for transaction. For example what is the payment status for transaction 700?")
						.inputType(PaymentInfoRequest.class)
						.build()))
			.build();

		ChatResponse chatResponse = this.chatModel.call(new Prompt(messages, promptOptions));

		assertThat(chatResponse).isNotNull();
		logger.info("Response: {}", chatResponse);
		assertThat(chatResponse.getResult().getOutput().getText()).contains("30", "10", "15");

		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(150).isLessThan(500);

		ChatResponse response2 = this.chatModel
			.call(new Prompt("What is the payment status for transaction 696?", promptOptions));

		logger.info("Response: {}", response2);

		assertThat(response2.getResult().getOutput().getText()).containsIgnoringCase("transaction 696 is PAYED");

	}

	@Test
	public void functionCallTestInferredOpenApiSchemaStream() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in Tokyo? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String responseString = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		logger.info("Response: {}", responseString);

		assertThat(responseString).contains("10");

	}

	@Test
	public void functionCallUsageTestInferredOpenApiSchemaStreamFlash20() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Paris and in Tokyo? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.toolCallbacks(List.of(
					FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
						.description("Get the current weather in a given location.")
						.inputType(MockWeatherService.Request.class)
						.build(),
					FunctionToolCallback.builder("get_payment_status", new PaymentStatus())
						.description(
								"Retrieves the payment status for transaction. For example what is the payment status for transaction 700?")
						.inputType(PaymentInfoRequest.class)
						.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		ChatResponse chatResponse = response.blockLast();

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(150).isLessThan(500);

	}

	@Test
	public void functionCallUsageTestInferredOpenApiSchemaStreamFlash25() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Paris and in Tokyo? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
			.toolCallbacks(List.of(
					FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
						.description("Get the current weather in a given location.")
						.inputType(MockWeatherService.Request.class)
						.build(),
					FunctionToolCallback.builder("get_payment_status", new PaymentStatus())
						.description(
								"Retrieves the payment status for transaction. For example what is the payment status for transaction 700?")
						.inputType(PaymentInfoRequest.class)
						.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		ChatResponse chatResponse = response.blockLast();

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(150).isLessThan(600);

	}

	public record PaymentInfoRequest(String id) {

	}

	public record TransactionStatus(String status) {

	}

	public static class PaymentStatus implements Function<PaymentInfoRequest, TransactionStatus> {

		@Override
		public TransactionStatus apply(PaymentInfoRequest paymentInfoRequest) {
			return new TransactionStatus("Transaction " + paymentInfoRequest.id() + " is PAYED");
		}

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Client genAiClient() {
			String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
			String location = System.getenv("GOOGLE_CLOUD_LOCATION");
			return Client.builder().project(projectId).location(location).vertexAI(true).build();
		}

		@Bean
		public GoogleGenAiChatModel vertexAiEmbedding(Client genAiClient) {
			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.defaultOptions(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
					.temperature(0.9)
					.build())
				.build();
		}

	}

}
