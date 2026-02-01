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

package org.springframework.ai.google.genai;

import java.util.stream.Stream;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ThinkingLevel validation with Gemini 3 models.
 *
 * <p>
 * Gemini 3 Pro only supports LOW and HIGH thinking levels. Gemini 3 Flash supports all
 * levels (MINIMAL, LOW, MEDIUM, HIGH).
 *
 * @author Dan Dobrin
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
class GoogleGenAiThinkingLevelIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiThinkingLevelIT.class);

	private Client genAiClient;

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("GOOGLE_API_KEY");
		this.genAiClient = Client.builder().apiKey(apiKey).build();
	}

	static Stream<Arguments> proModelUnsupportedLevels() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.MINIMAL),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM));
	}

	static Stream<Arguments> proModelSupportedLevels() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.HIGH));
	}

	static Stream<Arguments> flashModelAllLevels() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.MINIMAL),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.HIGH));
	}

	@ParameterizedTest
	@MethodSource("proModelUnsupportedLevels")
	void testGemini3ProRejectsUnsupportedLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		assertThatThrownBy(() -> chatModel.call(new Prompt("What is 2+2?")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(level.name())
			.hasMessageContaining("not supported")
			.hasMessageContaining("Gemini 3 Pro");

		logger.info("Correctly rejected ThinkingLevel.{} for model {}", level, modelName);
	}

	@ParameterizedTest
	@MethodSource("proModelSupportedLevels")
	void testGemini3ProAcceptsSupportedLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		var response = chatModel.call(new Prompt("What is 2+2? Answer with just the number."));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();
		logger.info("Successfully used ThinkingLevel.{} with model {}. Response: {}", level, modelName,
				response.getResult().getOutput().getText());
	}

	@ParameterizedTest
	@MethodSource("flashModelAllLevels")
	void testGemini3FlashAcceptsAllLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		var response = chatModel.call(new Prompt("What is 2+2? Answer with just the number."));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();
		logger.info("Successfully used ThinkingLevel.{} with model {}. Response: {}", level, modelName,
				response.getResult().getOutput().getText());
	}

}
