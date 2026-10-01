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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.google.genai.transcription.GoogleGenAiTranscriptionModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GoogleGenAiTranscriptionAutoConfiguration}.
 *
 * <p>
 * Activated when {@code GOOGLE_CLOUD_PROJECT} is present (Speech-to-Text V2 always
 * requires a project). Tests are skipped when the environment variable is absent.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionAutoConfigurationIT {

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
	void transcriptionWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues(
					"spring.ai.google.genai.transcription.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.transcription.location=global",
					"spring.ai.google.genai.transcription.model=chirp_2",
					"spring.ai.google.genai.transcription.language-codes=en-US")
			.withConfiguration(AutoConfigurations.of(GoogleGenAiTranscriptionAutoConfiguration.class,
					GoogleGenAiTranscriptionConnectionAutoConfiguration.class, SpringAiRetryAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiTranscriptionModel transcriptionModel = context.getBean(GoogleGenAiTranscriptionModel.class);
			AudioTranscriptionResponse response = transcriptionModel
				.call(new AudioTranscriptionPrompt(new ClassPathResource("speech-mono.wav")));
			assertThat(response.getResult().getOutput()).isNotNull();
		});
	}

	@Test
	void transcriptionModelActivation() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
				"spring.ai.google.genai.transcription.api-key=test-key",
				"spring.ai.google.genai.transcription.project-id=test-project");

		contextRunner
			.withConfiguration(AutoConfigurations.of(GoogleGenAiTranscriptionAutoConfiguration.class,
					GoogleGenAiTranscriptionConnectionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=none")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiTranscriptionProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).isEmpty();
			});

		contextRunner
			.withConfiguration(AutoConfigurations.of(GoogleGenAiTranscriptionAutoConfiguration.class,
					GoogleGenAiTranscriptionConnectionAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=google-genai")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiTranscriptionProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).isNotEmpty();
			});
	}

}
