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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGenAiEmbeddingConnectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
				GoogleGenAiTextEmbeddingAutoConfiguration.class));

	@Test
	void sharedApiKeyConfiguresEmbeddingModel() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(GoogleGenAiTextEmbeddingModel.class);
			assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
				.isEqualTo("shared-test-key");
		});
	}

	@Test
	void sharedApiKeyConfiguresChatAndEmbeddingModels() {
		this.contextRunner
			.withConfiguration(
					AutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(GoogleGenAiChatModel.class);
				assertThat(context).hasSingleBean(GoogleGenAiTextEmbeddingModel.class);
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("shared-test-key");
			});
	}

	@Test
	void embeddingApiKeyOverridesSharedApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key",
					"spring.ai.google.genai.embedding.api-key=embedding-test-key")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("embedding-test-key");
			});
	}

	@Test
	void blankEmbeddingApiKeyFallsBackToSharedApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key",
					"spring.ai.google.genai.embedding.api-key= ")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("shared-test-key");
			});
	}

	@Test
	void embeddingApiKeyWorksWithoutSharedConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.embedding.api-key=embedding-test-key")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(GoogleGenAiTextEmbeddingModel.class);
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("embedding-test-key");
			});
	}

	@Test
	void embeddingProjectDoesNotFallBackToSharedApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key",
					"spring.ai.google.genai.embedding.project-id=embedding-project")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Google GenAI location must be set!");
			});
	}

	@Test
	void explicitVertexAiDoesNotFallBackToSharedApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key",
					"spring.ai.google.genai.embedding.vertex-ai=true")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Google GenAI project-id must be set!");
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { "location=us-central1", "credentials-uri=classpath:unused-credentials.json" })
	void embeddingVertexAiSettingsDoNotFallBackToSharedApiKey(String property) {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key",
					"spring.ai.google.genai.embedding." + property)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Google GenAI project-id must be set!");
			});
	}

	@Test
	void customConnectionDetailsTakePrecedence() {
		var connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder().apiKey("custom-test-key").build();
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=shared-test-key")
			.withBean(GoogleGenAiEmbeddingConnectionDetails.class, () -> connectionDetails)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class);
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class)).isSameAs(connectionDetails);
			});
	}

	@Test
	void missingConnectionConfigurationStillFails() {
		this.contextRunner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Google GenAI project-id must be set!");
		});
	}

}
