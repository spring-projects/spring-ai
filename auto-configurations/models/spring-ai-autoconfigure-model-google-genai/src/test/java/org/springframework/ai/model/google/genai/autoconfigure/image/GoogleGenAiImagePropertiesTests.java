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
			.withPropertyValues("spring.ai.google.genai.image.api-key=test-key",
					"spring.ai.google.genai.image.project-id=test-project",
					"spring.ai.google.genai.image.location=us-central1")
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
			.withPropertyValues("spring.ai.google.genai.image.options.model=imagen-4.0-ultra-generate-001",
					"spring.ai.google.genai.image.options.n=2",
					"spring.ai.google.genai.image.options.aspect-ratio=16:9",
					"spring.ai.google.genai.image.options.negative-prompt=blurry",
					"spring.ai.google.genai.image.options.guidance-scale=5.0",
					"spring.ai.google.genai.image.options.seed=42",
					"spring.ai.google.genai.image.options.safety-filter-level=BLOCK_ONLY_HIGH",
					"spring.ai.google.genai.image.options.person-generation=ALLOW_ADULT",
					"spring.ai.google.genai.image.options.include-safety-attributes=true",
					"spring.ai.google.genai.image.options.include-rai-reason=true",
					"spring.ai.google.genai.image.options.language=en",
					"spring.ai.google.genai.image.options.output-mime-type=image/png",
					"spring.ai.google.genai.image.options.output-compression-quality=80",
					"spring.ai.google.genai.image.options.add-watermark=true",
					"spring.ai.google.genai.image.options.image-size=2K",
					"spring.ai.google.genai.image.options.enhance-prompt=true",
					"spring.ai.google.genai.image.options.output-gcs-uri=gs://bucket/out",
					"spring.ai.google.genai.image.options.labels.env=test")
			.run(context -> {
				GoogleGenAiImageProperties props = context.getBean(GoogleGenAiImageProperties.class);
				GoogleGenAiImageOptions options = props.getOptions();
				assertThat(options.getModel()).isEqualTo("imagen-4.0-ultra-generate-001");
				assertThat(options.getN()).isEqualTo(2);
				assertThat(options.getAspectRatio()).isEqualTo("16:9");
				assertThat(options.getNegativePrompt()).isEqualTo("blurry");
				assertThat(options.getGuidanceScale()).isEqualTo(5.0f);
				assertThat(options.getSeed()).isEqualTo(42);
				assertThat(options.getSafetyFilterLevel())
					.isEqualTo(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
				assertThat(options.getPersonGeneration())
					.isEqualTo(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
				assertThat(options.getIncludeSafetyAttributes()).isTrue();
				assertThat(options.getIncludeRaiReason()).isTrue();
				assertThat(options.getLanguage()).isEqualTo("en");
				assertThat(options.getOutputMimeType()).isEqualTo("image/png");
				assertThat(options.getOutputCompressionQuality()).isEqualTo(80);
				assertThat(options.getAddWatermark()).isTrue();
				assertThat(options.getImageSize()).isEqualTo("2K");
				assertThat(options.getEnhancePrompt()).isTrue();
				assertThat(options.getOutputGcsUri()).isEqualTo("gs://bucket/out");
				assertThat(options.getLabels()).containsEntry("env", "test");
			});
	}

	@Test
	void defaultOptionsBinding() {
		this.contextRunner.run(context -> {
			GoogleGenAiImageProperties props = context.getBean(GoogleGenAiImageProperties.class);
			assertThat(props.getOptions().getModel()).isEqualTo("imagen-4.0-generate-001");
			assertThat(props.getOptions().getAspectRatio()).isEqualTo("1:1");
		});
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiImageConnectionProperties.class, GoogleGenAiImageProperties.class })
	static class PropertiesTestConfiguration {

	}

}
