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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.image.GoogleGenAiImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GoogleGenAiImageAutoConfiguration}.
 *
 * <p>
 * Activated via either {@code GOOGLE_API_KEY} (Gemini Developer API) or
 * {@code GOOGLE_CLOUD_PROJECT} + {@code GOOGLE_CLOUD_LOCATION} (Vertex AI). Tests are
 * skipped when the corresponding environment variables are absent.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageAutoConfigurationIT {

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
	void imageWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.image.api-key=" + System.getenv("GOOGLE_API_KEY"),
					"spring.ai.google.genai.image.model=gemini-2.5-flash-image", "spring.ai.google.genai.image.n=1")
			.withConfiguration(AutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class,
					SpringAiRetryAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiImageModel imageModel = context.getBean(GoogleGenAiImageModel.class);
			ImageResponse response = imageModel.call(new ImagePrompt("A simple red apple"));
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResults().get(0).getOutput()).isNotNull();
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".+")
	void imageWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.image.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.image.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"),
					"spring.ai.google.genai.image.model=gemini-2.5-flash-image", "spring.ai.google.genai.image.n=1")
			.withConfiguration(AutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class,
					SpringAiRetryAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiImageModel imageModel = context.getBean(GoogleGenAiImageModel.class);
			ImageResponse response = imageModel.call(new ImagePrompt("A simple red apple"));
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResults().get(0).getOutput()).isNotNull();
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
	void imageModelActivation() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.image.api-key=test-key");

		// Test that image model is not activated when disabled
		contextRunner
			.withConfiguration(AutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class,
					GoogleGenAiImageConnectionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=none")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).isEmpty();
			});

		// Test that image model is activated when enabled
		contextRunner
			.withConfiguration(AutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class,
					GoogleGenAiImageConnectionAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.image=google-genai")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiImageModel.class)).isNotEmpty();
			});
	}

}
