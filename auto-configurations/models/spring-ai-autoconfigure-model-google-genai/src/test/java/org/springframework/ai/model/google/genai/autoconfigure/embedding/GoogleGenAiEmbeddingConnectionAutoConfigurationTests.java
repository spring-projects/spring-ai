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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiEmbeddingConnectionAutoConfiguration}.
 */
class GoogleGenAiEmbeddingConnectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiEmbeddingConnectionAutoConfiguration.class));

	@Test
	void apiKeyFromSharedConnectionProperties() {
		// The shared spring.ai.google.genai.api-key must be enough to start the
		// embedding module (Fixes #7037).
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=test-key").run(context -> {
			assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class);
			GoogleGenAiEmbeddingConnectionDetails details = context
				.getBean(GoogleGenAiEmbeddingConnectionDetails.class);
			assertThat(details.getApiKey()).isEqualTo("test-key");
			assertThat(details.getProjectId()).isNull();
		});
	}

	@Test
	void embeddingApiKeyTakesPrecedenceOverSharedApiKey() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.embedding.api-key=embedding-key",
					"spring.ai.google.genai.api-key=shared-key")
			.run(context -> {
				assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class);
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("embedding-key");
			});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
	void projectAndLocationFromSharedConnectionProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.project-id=test-project",
					"spring.ai.google.genai.location=us-central1")
			.run(context -> {
				assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class);
				GoogleGenAiEmbeddingConnectionDetails details = context
					.getBean(GoogleGenAiEmbeddingConnectionDetails.class);
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getLocation()).isEqualTo("us-central1");
				assertThat(details.getApiKey()).isNull();
			});
	}

	@Test
	void incompleteConfigurationFailsWithHelpfulMessage() {
		this.contextRunner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("spring.ai.google.genai.embedding.api-key")
				.hasStackTraceContaining("spring.ai.google.genai.api-key");
		});
	}

	@Test
	void vertexAiFlagWithoutVertexConfigFails() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.embedding.vertex-ai=true").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasStackTraceContaining("Vertex AI mode requires both 'project-id' and 'location' to be configured.");
		});
	}

	@Test
	void customConnectionDetailsBeanBacksOff() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=test-key")
			.withBean("customConnectionDetails", GoogleGenAiEmbeddingConnectionDetails.class,
					() -> GoogleGenAiEmbeddingConnectionDetails.builder().apiKey("custom-key").build())
			.run(context -> {
				assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class);
				assertThat(context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getApiKey())
					.isEqualTo("custom-key");
			});
	}

}
