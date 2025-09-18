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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI properties binding.
 */
public class GoogleGenAiPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=test-key",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1")
			.run(context -> {
				GoogleGenAiConnectionProperties connectionProperties = context
					.getBean(GoogleGenAiConnectionProperties.class);
				assertThat(connectionProperties.getApiKey()).isEqualTo("test-key");
				assertThat(connectionProperties.getProjectId()).isEqualTo("test-project");
				assertThat(connectionProperties.getLocation()).isEqualTo("us-central1");
			});
	}

	@Test
	void chatPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.chat.options.model=gemini-2.0-flash",
					"spring.ai.google.genai.chat.options.temperature=0.5",
					"spring.ai.google.genai.chat.options.max-output-tokens=2048",
					"spring.ai.google.genai.chat.options.top-p=0.9",
					"spring.ai.google.genai.chat.options.response-mime-type=application/json")
			.run(context -> {
				GoogleGenAiChatProperties chatProperties = context.getBean(GoogleGenAiChatProperties.class);
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("gemini-2.0-flash");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.5);
				assertThat(chatProperties.getOptions().getMaxOutputTokens()).isEqualTo(2048);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.9);
				assertThat(chatProperties.getOptions().getResponseMimeType()).isEqualTo("application/json");
			});
	}

	@Test
	void embeddingPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.embedding.api-key=embedding-key",
					"spring.ai.google.genai.embedding.project-id=embedding-project",
					"spring.ai.google.genai.embedding.location=europe-west1")
			.run(context -> {
				GoogleGenAiEmbeddingConnectionProperties embeddingProperties = context
					.getBean(GoogleGenAiEmbeddingConnectionProperties.class);
				assertThat(embeddingProperties.getApiKey()).isEqualTo("embedding-key");
				assertThat(embeddingProperties.getProjectId()).isEqualTo("embedding-project");
				assertThat(embeddingProperties.getLocation()).isEqualTo("europe-west1");
			});
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiConnectionProperties.class, GoogleGenAiChatProperties.class,
			GoogleGenAiEmbeddingConnectionProperties.class })
	static class PropertiesTestConfiguration {

	}

}
