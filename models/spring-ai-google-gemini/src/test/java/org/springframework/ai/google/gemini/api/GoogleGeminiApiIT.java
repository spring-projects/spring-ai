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
package org.springframework.ai.google.gemini.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.gemini.GoogleGeminiChatModel;
import org.springframework.ai.google.gemini.GoogleGeminiChatOptions;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletion;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletionMessage;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletionRequest;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GoogleGeminiApiIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGeminiApiIT.class);

	GoogleGeminiApi api = new GoogleGeminiApi(GoogleGeminiApi.ChatModel.GEMINI_2_5_FLASH_LITE.value,
			System.getenv("GEMINI_API_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(ChatCompletionMessage.Role.USER,
				"Hello world");
		ResponseEntity<ChatCompletion> response = api
			.chatCompletionEntity(new ChatCompletionRequest(List.of(chatCompletionMessage), null, null, null));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatIT() {
		var options = GoogleGeminiChatOptions.builder().withMaxOutputTokens(400).withThinkingBudget(512).build();
		var model = new GoogleGeminiChatModel(api);
		var output = model
			.call(new Prompt("Provide a list of 3 famous physicists and their key contributions", options));
		assertThat(output).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(ChatCompletionMessage.Role.USER,
				"Hello world");
		var response = api
			.chatCompletionStream(new ChatCompletionRequest(List.of(chatCompletionMessage), null, null, null));

		assertThat(response).isNotNull();
		var chunks = response.collectList().block();
		assertThat(chunks).isNotNull();
	}

	@Test
	void functionCallTest() {
		GoogleGeminiChatOptions promptOptions = GoogleGeminiChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Generate 3 tool calls");
		List<Message> messages = new ArrayList<>(List.of(userMessage));
		var model = new GoogleGeminiChatModel(api, promptOptions);
		var prompt = new Prompt(messages, promptOptions);
		ChatResponse response = model.call(prompt);
		logger.info("Response: {}", response);
		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		Flux<ChatResponse> responseFlux = model.stream(prompt);
		String responseAwaited = responseFlux.collectList()
			.block(Duration.ofSeconds(20))
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response awaited: {}", responseAwaited);
		assertThat(responseAwaited).contains("30", "10", "15");
	}

}
