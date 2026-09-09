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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationConvention;
import org.springframework.ai.google.genai.transcription.GoogleGenAiTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiTranscriptionAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.google.genai.transcription.api-key=test-key",
				"spring.ai.google.genai.transcription.project-id=test-project")
		.withConfiguration(AutoConfigurations.of(GoogleGenAiTranscriptionAutoConfiguration.class,
				GoogleGenAiTranscriptionConnectionAutoConfiguration.class));

	@Test
	void createsTranscriptionModelWithDefaultRetryTemplateWhenNoneProvided() {
		this.contextRunner
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).hasSize(1));
	}

	@Test
	void appliesCustomObservationConventionWhenProvided() {
		AudioTranscriptionModelObservationConvention customConvention = Mockito
			.mock(AudioTranscriptionModelObservationConvention.class);

		this.contextRunner.withBean(AudioTranscriptionModelObservationConvention.class, () -> customConvention)
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).hasSize(1);
				assertThat(context.getBean(AudioTranscriptionModelObservationConvention.class))
					.isSameAs(customConvention);
			});
	}

	@Test
	void usesCustomObservationRegistryWhenProvided() {
		ObservationRegistry customRegistry = ObservationRegistry.create();

		this.contextRunner.withBean(ObservationRegistry.class, () -> customRegistry)
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).hasSize(1));
	}

	@Test
	void disabledWhenTranscriptionModelPropertyIsAnotherProvider() {
		this.contextRunner.withPropertyValues("spring.ai.model.audio.transcription=none")
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).isEmpty());
	}

	@Test
	void backsOffWhenUserSuppliesOwnTranscriptionModel() {
		this.contextRunner.withUserConfiguration(CustomTranscriptionModelConfiguration.class).run(context -> {
			assertThat(context.getBeansOfType(GoogleGenAiTranscriptionModel.class)).hasSize(1);
			assertThat(context.getBean(GoogleGenAiTranscriptionModel.class))
				.isSameAs(CustomTranscriptionModelConfiguration.CUSTOM_MODEL);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTranscriptionModelConfiguration {

		static final GoogleGenAiTranscriptionModel CUSTOM_MODEL = Mockito.mock(GoogleGenAiTranscriptionModel.class);

		@Bean
		GoogleGenAiTranscriptionModel googleGenAiTranscriptionModel() {
			return CUSTOM_MODEL;
		}

	}

}
