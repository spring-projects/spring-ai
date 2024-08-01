/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.openai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = OpenAiChatModeAdditionalHttpHeadersIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatModeAdditionalHttpHeadersIT {

	@Autowired
	private OpenAiChatModel openAiChatModel;

	@Test
	void additionalApiKeyHeader() {

		assertThrows(NonTransientAiException.class, () -> {
			this.openAiChatModel.call("Tell me a joke");
		});

		// Use the additional headers to override the Api Key.
		// Mind that you have to prefix the Api Key with the "Bearer " prefix.
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.withHttpHeaders(Map.of("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY")))
			.build();

		ChatResponse response = this.openAiChatModel.call(new Prompt("Tell me a joke", options));

		assertThat(response).isNotNull();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi("Invalid API Key");
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatModel(openAiApi);
		}

	}

}
