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

import com.google.genai.Client;

import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for Google GenAI with serviceTier priority.
 */
class GoogleGenAiChatPriorityIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiChatPriorityIT.class);

	@ParameterizedTest
	@ValueSource(strings = { "flex", "standard", "priority", "unsupported" })
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
	void testPriorityServiceTier(String serviceTier) {
		Client genAiClient = Client.builder().apiKey(System.getenv("GOOGLE_API_KEY")).build();
		runTest(genAiClient, serviceTier);
	}

	@Disabled(value = "Current Vertex AI backend dont support it yet")
	@ParameterizedTest
	@ValueSource(strings = { "flex", "standard", "priority", "unsupported" })
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".+")
	void testPriorityServiceTierVertex(String serviceTier) {
		Client genAiClient = Client.builder()
			.project(System.getenv("GOOGLE_CLOUD_PROJECT"))
			.location(System.getenv("GOOGLE_CLOUD_LOCATION"))
			.vertexAI(true)
			.build();
		runTest(genAiClient, serviceTier);
	}

	private void runTest(Client genAiClient, String serviceTier) {
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH)
				.serviceTier(serviceTier)
				.build())
			.build();

		try {
			var response = chatModel.call(new Prompt("Explain the importance of service tiers in cloud APIs."));

			if ("unsupported".equals(serviceTier)) {
				fail("Unsupported service tier must cause failure");
			}

			assertThat(response).isNotNull();
			assertThat(response.getResult()).isNotNull();
			assertThat(response.getResult().getOutput().getText()).isNotBlank();
			logger.info("Successfully used ServiceTier.{}. Response: {}", serviceTier,
					response.getResult().getOutput().getText());
		}
		catch (Exception e) {
			if (!"unsupported".equals(serviceTier)) {
				fail("Supported service tier should not cause failure");
			}

			if (e.getCause() instanceof ClientException clientException) {
				assertEquals(400, clientException.code());
				assertTrue(clientException.getMessage().contains("Invalid value at 'service_tier'"));
				logger.info("Failure using ServiceTier.{}. (Expected)", serviceTier);
			}
			else {
				fail("Unexpected failure: " + e.getMessage());
			}
		}
	}

}
