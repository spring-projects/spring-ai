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

package org.springframework.ai.anthropic;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = AnthropicChatModelAdditionalHttpHeadersIT.Config.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicChatModelAdditionalHttpHeadersIT {

	@Autowired
	private AnthropicChatModel chatModel;

	@Test
	void additionalApiKeyHeader() {

		assertThatThrownBy(() -> this.chatModel.call("Tell me a joke")).isInstanceOf(NonTransientAiException.class);

		// Use the additional headers to override the Api Key.
		// Mind that you have to prefix the Api Key with the "Bearer " prefix.
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.httpHeaders(Map.of("x-api-key", System.getenv("ANTHROPIC_API_KEY")))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Tell me a joke", options));

		assertThat(response).isNotNull();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public AnthropicApi anthropicApi() {
			return new AnthropicApi("Invalid API Key");
		}

		@Bean
		public AnthropicChatModel anthropicChatModel(AnthropicApi api) {
			return AnthropicChatModel.builder().anthropicApi(api).build();
		}

	}

}
