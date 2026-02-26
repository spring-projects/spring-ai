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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.GoogleGenAiImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Google GenAI Image autoconfiguration.
 *
 * Works in 2 configuration options: 1. GOOGLE_API_KEY (Gemini Developer API) 2.
 * GOOGLE_CLOUD_PROJECT + GOOGLE_CLOUD_LOCATION (Vertex AI)
 *
 * @author Danil Temnikov
 */
class GoogleGenAiImageAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(GoogleGenAiImageAutoConfigurationIT.class);

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void generateImageWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiImageModel imageModel = context.getBean(GoogleGenAiImageModel.class);

			ImagePrompt prompt = new ImagePrompt("A simple black square icon");
			ImageResponse response = imageModel.call(prompt);

			assertThat(response).isNotNull();
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResult()).isNotNull();

			logger.info("Generated image count: " + response.getResults().size());
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void generateImageWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiImageAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiImageModel imageModel = context.getBean(GoogleGenAiImageModel.class);

			ImagePrompt prompt = new ImagePrompt("A blue circle on white background");
			ImageResponse response = imageModel.call(prompt);

			assertThat(response).isNotNull();
			assertThat(response.getResults()).isNotEmpty();
			assertThat(response.getResult()).isNotNull();

			logger.info("Generated image count (Vertex AI): " + response.getResults().size());
		});
	}

}
