/*
 * Copyright 2025-2025 the original author or authors.
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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.genai.Client;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.test.chat.client.advisor.AbstractToolCallAdvisorIT;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ToolCallAdvisor} functionality.
 *
 * @author Christian Tzolov
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
class GoogleGenAiToolCallAdvisorIT extends AbstractToolCallAdvisorIT {

	@Test
	@Disabled
	void streamWithDefaultAdvisorConfiguration1() {

		var chatClient = ChatClient.builder(getChatModel()).build();

		Flux<String> response = chatClient.prompt()
			.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
			.toolCallbacks(createWeatherToolCallback())
			.stream()
			.content();

		List<String> chunks = response.collectList().block();
		String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@Override
	protected ChatModel getChatModel() {

		GoogleGenAiChatModel.ChatModel model = GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW;

		String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
		String location = "global";
		var genAiClient = Client.builder().project(projectId).location(location).vertexAI(true).build();

		return GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient)
			.defaultOptions(GoogleGenAiChatOptions.builder().model(model).build())
			.build();

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

	}

}
