/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Google GenAI Text Embedding autoconfiguration.
 *
 * This test can run in two modes: 1. With GOOGLE_API_KEY environment variable (Gemini
 * Developer API mode) 2. With GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION environment
 * variables (Vertex AI mode)
 */
public class GoogleGenAiTextEmbeddingAutoConfigurationIT {

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void embeddingWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.embedding.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiTextEmbeddingAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiTextEmbeddingModel embeddingModel = context.getBean(GoogleGenAiTextEmbeddingModel.class);

			EmbeddingResponse embeddingResponse = embeddingModel
				.embedForResponse(List.of("Hello World", "World is big"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getMetadata().getModel()).isNotNull();
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void embeddingWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.embedding.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.embedding.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiTextEmbeddingAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiTextEmbeddingModel embeddingModel = context.getBean(GoogleGenAiTextEmbeddingModel.class);

			EmbeddingResponse embeddingResponse = embeddingModel
				.embedForResponse(List.of("Hello World", "World is big"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getMetadata().getModel()).isNotNull();
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void embeddingModelActivation() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.embedding.api-key=" + System.getenv("GOOGLE_API_KEY"));

		// Test that embedding model is not activated when disabled
		contextRunner
			.withConfiguration(AutoConfigurations.of(GoogleGenAiTextEmbeddingAutoConfiguration.class,
					GoogleGenAiEmbeddingConnectionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding.text=none")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiTextEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiTextEmbeddingModel.class)).isEmpty();
			});

		// Test that embedding model is activated when enabled
		contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiTextEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding.text=google-genai")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiTextEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiTextEmbeddingModel.class)).isNotEmpty();
			});
	}

}
