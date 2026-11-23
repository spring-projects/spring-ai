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

import org.springframework.ai.google.genai.image.GoogleGenAiImageOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI Image properties binding.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImagePropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.api-key=test-key",
					"spring.ai.google.genai.project-id=test-project", "spring.ai.google.genai.location=us-central1")
			.run(context -> {
				GoogleGenAiImageConnectionProperties props = context
					.getBean(GoogleGenAiImageConnectionProperties.class);
				assertThat(props.getApiKey()).isEqualTo("test-key");
				assertThat(props.getProjectId()).isEqualTo("test-project");
				assertThat(props.getLocation()).isEqualTo("us-central1");
			});
	}

	@Test
	void optionsPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.image.model=gemini-2.5-flash-image",
					"spring.ai.google.genai.image.n=2", "spring.ai.google.genai.image.aspect-ratio=16:9",
					"spring.ai.google.genai.image.seed=42",
					"spring.ai.google.genai.image.safety-filter-level=BLOCK_ONLY_HIGH",
					"spring.ai.google.genai.image.person-generation=ALLOW_ADULT",
					"spring.ai.google.genai.image.output-mime-type=image/png",
					"spring.ai.google.genai.image.output-compression-quality=80",
					"spring.ai.google.genai.image.image-size=2K", "spring.ai.google.genai.image.labels.env=test",
					"spring.ai.google.genai.image.temperature=0.7", "spring.ai.google.genai.image.top-p=0.9",
					"spring.ai.google.genai.image.top-k=40", "spring.ai.google.genai.image.max-output-tokens=1024")
			.run(context -> {
				GoogleGenAiImageProperties props = context.getBean(GoogleGenAiImageProperties.class);
				GoogleGenAiImageOptions options = props.toOptions();
				assertThat(options.getModel()).isEqualTo("gemini-2.5-flash-image");
				assertThat(options.getN()).isEqualTo(2);
				assertThat(options.getAspectRatio()).isEqualTo("16:9");
				assertThat(options.getSeed()).isEqualTo(42);
				assertThat(options.getSafetyFilterLevel())
					.isEqualTo(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
				assertThat(options.getPersonGeneration())
					.isEqualTo(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
				assertThat(options.getOutputMimeType()).isEqualTo("image/png");
				assertThat(options.getOutputCompressionQuality()).isEqualTo(80);
				assertThat(options.getImageSize()).isEqualTo("2K");
				assertThat(options.getLabels()).containsEntry("env", "test");
				assertThat(options.getTemperature()).isEqualTo(0.7f);
				assertThat(options.getTopP()).isEqualTo(0.9f);
				assertThat(options.getTopK()).isEqualTo(40.0f);
				assertThat(options.getMaxOutputTokens()).isEqualTo(1024);
			});
	}

	@Test
	void defaultOptionsBinding() {
		this.contextRunner.run(context -> {
			GoogleGenAiImageProperties props = context.getBean(GoogleGenAiImageProperties.class);
			assertThat(props.toOptions().getModel()).isEqualTo(GoogleGenAiImageOptions.DEFAULT_MODEL_NAME);
		});
	}

	@Test
	void connectionPropertiesGettersAndSetters() {
		GoogleGenAiImageConnectionProperties props = new GoogleGenAiImageConnectionProperties();
		props.setApiKey("api-key");
		props.setProjectId("project-id");
		props.setLocation("location");
		props.setVertexAi(true);
		org.springframework.core.io.Resource credentialsUri = new org.springframework.core.io.ClassPathResource(
				"fake-credentials.json");
		props.setCredentialsUri(credentialsUri);

		assertThat(props.getApiKey()).isEqualTo("api-key");
		assertThat(props.getProjectId()).isEqualTo("project-id");
		assertThat(props.getLocation()).isEqualTo("location");
		assertThat(props.isVertexAi()).isTrue();
		assertThat(props.getCredentialsUri()).isEqualTo(credentialsUri);
	}

	@Test
	void connectionPropertiesVertexAiAndCredentialsUriBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.vertex-ai=true",
					"spring.ai.google.genai.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiImageConnectionProperties props = context
					.getBean(GoogleGenAiImageConnectionProperties.class);
				assertThat(props.isVertexAi()).isTrue();
				assertThat(props.getCredentialsUri()).isNotNull();
			});
	}

	@Test
	void imagePropertiesGettersAndSetters() {
		GoogleGenAiImageProperties props = new GoogleGenAiImageProperties();
		props.setModel("model");
		props.setN(2);
		props.setAspectRatio("16:9");
		props.setSeed(42);
		props.setSafetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
		props.setPersonGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
		props.setOutputMimeType("image/png");
		props.setOutputCompressionQuality(80);
		props.setImageSize("2K");
		props.setTemperature(0.7f);
		props.setTopP(0.9f);
		props.setTopK(40f);
		props.setMaxOutputTokens(1024);

		assertThat(props.getModel()).isEqualTo("model");
		assertThat(props.getN()).isEqualTo(2);
		assertThat(props.getAspectRatio()).isEqualTo("16:9");
		assertThat(props.getSeed()).isEqualTo(42);
		assertThat(props.getSafetyFilterLevel()).isEqualTo(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
		assertThat(props.getPersonGeneration()).isEqualTo(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
		assertThat(props.getOutputMimeType()).isEqualTo("image/png");
		assertThat(props.getOutputCompressionQuality()).isEqualTo(80);
		assertThat(props.getImageSize()).isEqualTo("2K");
		assertThat(props.getTemperature()).isEqualTo(0.7f);
		assertThat(props.getTopP()).isEqualTo(0.9f);
		assertThat(props.getTopK()).isEqualTo(40f);
		assertThat(props.getMaxOutputTokens()).isEqualTo(1024);
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiImageConnectionProperties.class, GoogleGenAiImageProperties.class })
	static class PropertiesTestConfiguration {

	}

}
