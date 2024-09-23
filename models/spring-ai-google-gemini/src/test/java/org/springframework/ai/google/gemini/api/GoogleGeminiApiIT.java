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
import org.springframework.ai.google.gemini.GoogleGeminiChatModel;
import org.springframework.ai.google.gemini.GoogleGeminiChatOptions;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletion;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletionMessage;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.ChatCompletionRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GoogleGeminiApiIT {

	GoogleGeminiApi api = new GoogleGeminiApi(GoogleGeminiApi.ChatModel.GEMINI_1_5_FLASH.value,
			System.getenv("GEMINI_API_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(ChatCompletionMessage.Role.USER,
				"Hello world");
		ResponseEntity<ChatCompletion> response = api
			.chatCompletionEntity(new ChatCompletionRequest(List.of(chatCompletionMessage), null, null));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatIT() {
		var options = GoogleGeminiChatOptions.builder().withMaxOutputTokens(400).withThinkingBudget(128).build();
		var model = new GoogleGeminiChatModel(api, options);
		var output = model.call("Provide a list of 3 famous physicists and their key contributions");
		assertThat(output).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(ChatCompletionMessage.Role.USER,
				"Hello world");
		var response = api.chatCompletionStream(new ChatCompletionRequest(List.of(chatCompletionMessage), null, null));

		assertThat(response).isNotNull();
		var chunks = response.collectList().block();
		assertThat(chunks).isNotNull();
	}

}
