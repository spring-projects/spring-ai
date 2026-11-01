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

package org.springframework.ai.google.genai.client;

import com.google.genai.Client;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.test.chat.client.advisor.AbstractToolCallConfirmationIT;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tool call confirmation with Gemini. Gemini returns function calls without ids, and with
 * thoughts included it attaches a thought signature to them that the next request of the
 * turn must send back: a paused reply that lost it fails when the conversation resumes.
 *
 * @author Christian Tzolov
 */
@SpringBootTest(classes = GoogleGenAiToolCallConfirmationIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiToolCallConfirmationIT extends AbstractToolCallConfirmationIT {

	@Override
	protected ChatModel getChatModel() {
		Client genAiClient = Client.builder().apiKey(System.getenv("GOOGLE_API_KEY")).build();
		return GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient)
			.options(GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_FLASH_PREVIEW)
				.includeThoughts(true)
				.build())
			.build();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

	}

}
