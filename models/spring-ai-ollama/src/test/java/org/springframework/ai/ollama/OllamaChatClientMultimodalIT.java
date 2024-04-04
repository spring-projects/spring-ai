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
package org.springframework.ai.ollama;

import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Disabled("For manual smoke testing only.")
class OllamaChatClientMultimodalIT {

	private static String MODEL = "llava";

	private static final Log logger = LogFactory.getLog(OllamaChatClientIT.class);

	@Container
	static GenericContainer<?> ollamaContainer = new GenericContainer<>("ollama/ollama:0.1.29").withExposedPorts(11434);

	static String baseUrl;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL);
		logger.info(MODEL + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	@Autowired
	private OllamaChatClient client;

	@Test
	void multiModalityTest() throws IOException {

		byte[] imageData = new ClassPathResource("/test.png").getContentAsByteArray();

		var userMessage = new UserMessage("Explain what do you see on this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)));

		ChatResponse response = client.call(new Prompt(List.of(userMessage)));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple", "basket");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return new OllamaApi(baseUrl);
		}

		@Bean
		public OllamaChatClient ollamaChat(OllamaApi ollamaApi) {
			return new OllamaChatClient(ollamaApi, OllamaOptions.create().withModel(MODEL).withTemperature(0.9f));
		}

	}

}