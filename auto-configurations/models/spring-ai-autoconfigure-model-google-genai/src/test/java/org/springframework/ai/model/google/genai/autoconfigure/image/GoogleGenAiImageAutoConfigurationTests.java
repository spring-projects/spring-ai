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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.google.genai.image.GoogleGenAiImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiImageAutoConfiguration}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.google.genai.api-key=test-key")
		.withConfiguration(AutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class,
				GoogleGenAiImageConnectionAutoConfiguration.class));

	@Test
	void createsImageModelWithDefaultRetryTemplateWhenNoneProvided() {
		// Covers the retryTemplate.getIfUnique(...) fallback branch (no RetryTemplate
		// bean available in the context).
		this.contextRunner.run(context -> assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).hasSize(1));
	}

	@Test
	void appliesCustomObservationConventionWhenProvided() {
		// Covers the observationConvention.ifAvailable(...) branch.
		ImageModelObservationConvention customConvention = Mockito.mock(ImageModelObservationConvention.class);

		this.contextRunner.withBean(ImageModelObservationConvention.class, () -> customConvention).run(context -> {
			assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).hasSize(1);
			assertThat(context.getBean(ImageModelObservationConvention.class)).isSameAs(customConvention);
		});
	}

	@Test
	void usesCustomObservationRegistryWhenProvided() {
		ObservationRegistry customRegistry = ObservationRegistry.create();

		this.contextRunner.withBean(ObservationRegistry.class, () -> customRegistry)
			.run(context -> assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).hasSize(1));
	}

	@Test
	void backsOffWhenUserSuppliesOwnImageModel() {
		this.contextRunner.withUserConfiguration(CustomImageModelConfiguration.class).run(context -> {
			assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).hasSize(1);
			assertThat(context.getBean(GoogleGenAiImageModel.class))
				.isSameAs(CustomImageModelConfiguration.CUSTOM_MODEL);
		});
	}

	@Configuration
	static class CustomImageModelConfiguration {

		static final GoogleGenAiImageModel CUSTOM_MODEL = Mockito.mock(GoogleGenAiImageModel.class);

		@Bean
		GoogleGenAiImageModel googleGenAiImageModel() {
			return CUSTOM_MODEL;
		}

	}

}
