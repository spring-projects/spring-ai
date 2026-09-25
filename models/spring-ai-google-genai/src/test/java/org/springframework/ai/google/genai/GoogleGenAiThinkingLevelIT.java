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

import java.util.stream.Stream;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gemini 3.1 Pro supports LOW, MEDIUM and HIGH thinking levels, but not MINIMAL. Gemini
 * 3.5 Flash supports all levels (MINIMAL, LOW, MEDIUM, HIGH). Gemini 2.5 Flash and Flash
 * Lite reject {@code thinkingLevel} entirely on the {@code generateContent} API. Gemini
 * 2.5 Pro is intentionally not covered here: its behavior depends on the account used.
 *
 * @author Dan Dobrin
 * @author Sebastien Deleuze
 * @author Dimitar Proynov
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiThinkingLevelIT {

	private Client genAiClient;

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("GOOGLE_API_KEY");
		this.genAiClient = Client.builder().apiKey(apiKey).build();
	}

	static Stream<Arguments> proModelUnsupportedLevels() {
		return Stream.of(Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue(),
				GoogleGenAiThinkingLevel.MINIMAL));
	}

	static Stream<Arguments> proModelSupportedLevels() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue(),
						GoogleGenAiThinkingLevel.HIGH));
	}

	static Stream<Arguments> flashModelAllLevels() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH.getValue(),
						GoogleGenAiThinkingLevel.MINIMAL),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH.getValue(), GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH.getValue(),
						GoogleGenAiThinkingLevel.HIGH));
	}

	static Stream<Arguments> gemini25FlashRejectsThinkingLevel() {
		return Stream.of(
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue(),
						GoogleGenAiThinkingLevel.MINIMAL),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue(), GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH.getValue(), GoogleGenAiThinkingLevel.HIGH),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH_LIGHT.getValue(),
						GoogleGenAiThinkingLevel.MINIMAL),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH_LIGHT.getValue(),
						GoogleGenAiThinkingLevel.LOW),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH_LIGHT.getValue(),
						GoogleGenAiThinkingLevel.MEDIUM),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH_LIGHT.getValue(),
						GoogleGenAiThinkingLevel.HIGH));
	}

	@ParameterizedTest
	@MethodSource("proModelUnsupportedLevels")
	void testGemini3ProRejectsUnsupportedLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		assertThatThrownBy(() -> chatModel.call(new Prompt("What is 2+2?")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(level.name())
			.hasMessageContaining("not supported")
			.hasMessageContaining(modelName);
	}

	@ParameterizedTest
	@MethodSource("proModelSupportedLevels")
	void testGemini3ProAcceptsSupportedLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		var response = chatModel.call(new Prompt("What is 2+2? Answer with just the number."));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();
	}

	@ParameterizedTest
	@MethodSource("flashModelAllLevels")
	void testGemini3FlashAcceptsAllLevels(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		var response = chatModel.call(new Prompt("What is 2+2? Answer with just the number."));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotBlank();
	}

	@ParameterizedTest
	@MethodSource("gemini25FlashRejectsThinkingLevel")
	void testGemini25FlashRejectsThinkingLevel(String modelName, GoogleGenAiThinkingLevel level) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.genAiClient)
			.options(GoogleGenAiChatOptions.builder().model(modelName).thinkingLevel(level).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		assertThatThrownBy(() -> chatModel.call(new Prompt("What is 2+2?")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(level.name())
			.hasMessageContaining("not supported")
			.hasMessageContaining(modelName);
	}

}
