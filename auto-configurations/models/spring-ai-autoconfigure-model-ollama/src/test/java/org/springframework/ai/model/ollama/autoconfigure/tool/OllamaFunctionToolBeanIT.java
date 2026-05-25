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

package org.springframework.ai.model.ollama.autoconfigure.tool;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.BaseOllamaIT;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for function-based tool calling in Ollama.
 *
 * @author Thomas Vitale
 */
class OllamaFunctionToolBeanIT extends BaseOllamaIT {

	private static final Logger logger = LoggerFactory.getLogger(OllamaFunctionToolBeanIT.class);

	private static final String MODEL_NAME = OllamaModel.QWEN_2_5_3B.getName();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.ollama.baseUrl=" + getBaseUrl(),
				"spring.ai.ollama.chat.model=" + MODEL_NAME,
				"spring.ai.ollama.chat.temperature=0.5",
				"spring.ai.ollama.chat.topK=10")
				// @formatter:on
		.withConfiguration(ollamaAutoConfig(OllamaChatAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@BeforeAll
	static void beforeAll() {
		initializeOllama(MODEL_NAME);
	}

	@Test
	void toolCallTest() {
		this.contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			MyTools myTools = context.getBean(MyTools.class);

			UserMessage userMessage = new UserMessage(
					"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.");

			OllamaChatOptions options = mergeOptions(chatModel,
					OllamaChatOptions.builder().toolCallbacks(ToolCallbacks.from(myTools)));
			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), options));

			logger.info("Response: {}", response);

			var result = response.getResult();
			assertThat(result).isNotNull();
			assertThat(result.getOutput().getText()).contains("30", "10", "15");
		});

	}

	static class MyTools {

		@SuppressWarnings("unused")
		@Tool(description = "Find the weather conditions, and temperatures for a location, like a city or state.")
		String weatherByLocation(String locationName) {
			var temperature = switch (locationName) {
				case "San Francisco" -> 30;
				case "Tokyo" -> 10;
				case "Paris" -> 15;
				default -> 0;
			};

			return "The temperature in " + locationName + " is " + temperature + " degrees Celsius.";
		}

	}

	@Configuration
	static class Config {

		@Bean
		MyTools myTools() {
			return new MyTools();
		}

	}

}
