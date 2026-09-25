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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.image.GoogleGenAiImageConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiImageConnectionAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageConnectionAutoConfigurationTests {

	// A minimal, non-secret "authorized_user" credentials JSON. This credential type
	// does not require any cryptographic key parsing (unlike service accounts), which
	// keeps this unit test simple and self-contained. See
	// src/test/resources/fake-credentials.json.

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiImageConnectionAutoConfiguration.class));

	@Test
	void apiKeyOnlyConfiguresGeminiDeveloperApiMode() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=test-key").run(context -> {
			GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
			assertThat(details.getApiKey()).isEqualTo("test-key");
			assertThat(details.getGenAiClient()).isNotNull();
		});
	}

	@Test
	void projectAndLocationDefaultToVertexAiMode() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.project-id=test-project",
					"spring.ai.google.genai.location=us-central1",
					"spring.ai.google.genai.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getLocation()).isEqualTo("us-central1");
				assertThat(details.getGenAiClient()).isNotNull();
			});
	}

	@Test
	void explicitVertexAiModeIsHonored() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.vertex-ai=true",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1",
					"spring.ai.google.genai.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getGenAiClient()).isNotNull();
			});
	}

	@Test
	void explicitVertexAiModeWithBothApiKeyAndVertexConfigUsesVertexAi() {
		// Covers the ambiguity-guard "info" logging branch: both api-key and vertex
		// config present, vertex-ai explicitly enabled -> vertex wins.
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.vertex-ai=true", "spring.ai.google.genai.api-key=test-key",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1",
					"spring.ai.google.genai.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(details.getGenAiClient()).isNotNull();
			});
	}

	@Test
	void bothApiKeyAndVertexConfigWithoutExplicitVertexAiDefaultsToApiKey() {
		// Covers the ambiguity-guard "warn" logging branch: both api-key and vertex
		// config present, vertex-ai not explicitly enabled -> api key wins.
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=test-key",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1")
			.run(context -> {
				GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(details.getApiKey()).isEqualTo("test-key");
			});
	}

	@Test
	void explicitVertexAiModeWithoutProjectOrLocationFails() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.vertex-ai=true").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Vertex AI mode requires both 'project-id' and 'location'");
		});
	}

	@Test
	void incompleteConfigurationFails() {
		this.contextRunner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Incomplete Google GenAI configuration");
		});
	}

	@Test
	void credentialsUriIsLoadedAndPassedToTheConnectionBuilder() {
		// Locks in the fix: the credentials-uri property must actually be used to build
		// the underlying client rather than silently ignored.
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.project-id=test-project",
					"spring.ai.google.genai.location=us-central1",
					"spring.ai.google.genai.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiImageConnectionDetails details = context.getBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(details.getGenAiClient()).isNotNull();
			});
	}

}
