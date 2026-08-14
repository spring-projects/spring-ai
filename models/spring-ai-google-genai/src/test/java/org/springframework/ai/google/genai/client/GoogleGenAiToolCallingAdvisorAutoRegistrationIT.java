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

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.test.chat.client.advisor.AbstractToolCallingAdvisorAutoRegistrationIT;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for auto-registration of {@link ToolCallingAdvisor} with Google GenAI
 * chat model.
 *
 * @author Christian Tzolov
 */
@SpringBootTest(classes = GoogleGenAiToolCallingAdvisorAutoRegistrationIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
@ActiveProfiles("logging-test")
class GoogleGenAiToolCallingAdvisorAutoRegistrationIT extends AbstractToolCallingAdvisorAutoRegistrationIT {

	@Override
	protected ChatModel getChatModel() {

		GoogleGenAiChatModel.ChatModel model = GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW;

		String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
		String location = "global";
		var genAiClient = Client.builder().project(projectId).location(location).vertexAI(true).build();

		return GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient)
			.options(GoogleGenAiChatOptions.builder().model(model).build())
			.build();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

	}

}
