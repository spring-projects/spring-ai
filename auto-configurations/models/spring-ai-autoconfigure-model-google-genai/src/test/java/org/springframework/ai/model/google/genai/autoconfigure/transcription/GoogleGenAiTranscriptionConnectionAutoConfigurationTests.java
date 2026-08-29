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

package org.springframework.ai.model.google.genai.autoconfigure.transcription;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.transcription.GoogleGenAiTranscriptionConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiTranscriptionConnectionAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionConnectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiTranscriptionConnectionAutoConfiguration.class));

	@Test
	void apiKeyAndProjectConfigureConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.api-key=test-key",
					"spring.ai.google.genai.transcription.project-id=test-project")
			.run(context -> {
				GoogleGenAiTranscriptionConnectionDetails details = context
					.getBean(GoogleGenAiTranscriptionConnectionDetails.class);
				assertThat(details.getApiKey()).isEqualTo("test-key");
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getLocation()).isEqualTo(GoogleGenAiTranscriptionConnectionDetails.DEFAULT_LOCATION);
				assertThat(details.getSpeechClient()).isNotNull();
			});
	}

	@Test
	void projectAndCredentialsUriConfigureConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.project-id=test-project",
					"spring.ai.google.genai.transcription.location=us",
					"spring.ai.google.genai.transcription.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiTranscriptionConnectionDetails details = context
					.getBean(GoogleGenAiTranscriptionConnectionDetails.class);
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getLocation()).isEqualTo("us");
				assertThat(details.getSpeechClient()).isNotNull();
			});
	}

	@Test
	void missingProjectIdFails() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.transcription.api-key=test-key").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("project-id must be set");
		});
	}

}
