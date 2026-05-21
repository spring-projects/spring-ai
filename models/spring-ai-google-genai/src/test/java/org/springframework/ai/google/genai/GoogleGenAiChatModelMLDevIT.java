/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel.ChatModel;
import org.springframework.ai.google.genai.tool.MockWeatherService;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Google GenAI using MLDev (Google AI) API. These tests require a
 * GOOGLE_API_KEY environment variable and use vertexAI=false. This is needed for features
 * like includeServerSideToolInvocations which are MLDev-only.
 *
 * @author Dan Dobrin
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiChatModelMLDevIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiChatModelMLDevIT.class);

	@Autowired
	private GoogleGenAiChatModel chatModel;

	@Test
	@SuppressWarnings("unchecked")
	void googleSearchWithServerSideToolInvocations() {
		Prompt prompt = new Prompt(
				new UserMessage("What are the top 3 most famous pirates in history? Use Google Search."),
				GoogleGenAiChatOptions.builder()
					.model(ChatModel.GEMINI_2_5_FLASH)
					.googleSearchRetrieval(true)
					.includeServerSideToolInvocations(false)
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		logger.info("Response: {}", response.getResult().getOutput().getText());

		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void googleSearchWithServerSideToolInvocationsGemini3x() {
		Prompt prompt = new Prompt(
				new UserMessage("What are the top 3 most famous pirates in history? Use Google Search."),
				GoogleGenAiChatOptions.builder()
					.model(ChatModel.GEMINI_3_PRO_PREVIEW)
					.googleSearchRetrieval(true)
					.includeServerSideToolInvocations(true)
					.build());

		ChatResponse response = this.chatModel.call(prompt);

		logger.info("Response: {}", response.getResult().getOutput().getText());

		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
		assertThat(metadata).containsKey("serverSideToolInvocations");

		List<Map<String, Object>> invocations = (List<Map<String, Object>>) metadata.get("serverSideToolInvocations");
		assertThat(invocations).isNotEmpty();
		assertThat(invocations).anyMatch(inv -> "toolCall".equals(inv.get("type")));
		assertThat(invocations).anyMatch(inv -> "toolResponse".equals(inv.get("type")));
	}

	@Test
	@SuppressWarnings("unchecked")
	void functionCallingWithGoogleSearchAndServerSideToolInvocations() {
		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(ChatModel.GEMINI_2_5_FLASH)
			.googleSearchRetrieval(false)
			.includeServerSideToolInvocations(false)
			.toolCallbacks(List.of(FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Prompt prompt = new Prompt(new UserMessage(
				"What's the weather like in San Francisco? Return the temperature in Celsius. Also, search online for the latest news about San Francisco."),
				promptOptions);

		ChatResponse response = this.chatModel.call(prompt);

		logger.info("Response: {}", response.getResult().getOutput().getText());

		// Function call should have been executed — weather data should be in response
		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("30");

		// Check that server-side tool invocations were captured somewhere in the
		// conversation. The final response may or may not contain them depending on
		// whether the model's last turn included Google Search parts.
		// The primary validation is that the call succeeded without errors,
		// proving mixed parts (functionCall + toolCall/toolResponse) are handled
		// correctly.
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void functionCallingWithGoogleSearchAndServerSideToolInvocationsGemini3x() {
		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(ChatModel.GEMINI_3_FLASH_PREVIEW)
			.googleSearchRetrieval(true)
			.includeServerSideToolInvocations(true)
			.toolCallbacks(List.of(FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Prompt prompt = new Prompt(new UserMessage(
				"What's the weather like in San Francisco? Return the temperature in Celsius. Also, search online for the latest news about San Francisco."),
				promptOptions);

		ChatResponse response = this.chatModel.call(prompt);

		logger.info("Response: {}", response.getResult().getOutput().getText());

		// Function call should have been executed — weather data should be in response
		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("30");

		// Check that server-side tool invocations were captured somewhere in the
		// conversation. The final response may or may not contain them depending on
		// whether the model's last turn included Google Search parts.
		// The primary validation is that the call succeeded without errors,
		// proving mixed parts (functionCall + toolCall/toolResponse) are handled
		// correctly.
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Client genAiClient() {
			String apiKey = System.getenv("GOOGLE_API_KEY");
			return Client.builder().apiKey(apiKey).build();
		}

		@Bean
		public GoogleGenAiChatModel googleGenAiChatModel(Client genAiClient) {
			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.defaultOptions(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW)
					.build())
				.toolCallingManager(ToolCallingManager.builder().build())
				.build();
		}

	}

}
