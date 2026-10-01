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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiTextToSpeechConnectionAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechConnectionAutoConfigurationTests {

	// A minimal, non-secret "authorized_user" credentials JSON that avoids any
	// cryptographic key parsing (unlike service accounts) and lets the client be built
	// without contacting Google Cloud. See src/test/resources/fake-credentials.json.

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiTextToSpeechConnectionAutoConfiguration.class));

	@Test
	void connectionDetailsCreatedWithProjectAndCredentials() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.project-id=test-project",
					"spring.ai.google.genai.tts.location=eu",
					"spring.ai.google.genai.tts.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiTextToSpeechConnectionDetails details = context
					.getBean(GoogleGenAiTextToSpeechConnectionDetails.class);
				assertThat(details.getProjectId()).isEqualTo("test-project");
				assertThat(details.getLocation()).isEqualTo("eu");
				assertThat(details.getTextToSpeechClient()).isNotNull();
			});
	}

	@Test
	void missingProjectIdFailsContext() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(
							"Google GenAI text to speech project-id must be set. Text-to-Speech V2 resource names require a project.");
			});
	}

}
