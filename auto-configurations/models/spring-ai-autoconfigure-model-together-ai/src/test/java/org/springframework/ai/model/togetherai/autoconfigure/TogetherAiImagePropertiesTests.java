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

package org.springframework.ai.model.togetherai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TogetherAiImagePropertiesTests {

	@Test
	void togetherAiImageProperties_shouldBindValues_whenPropertiesAreSet() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.togetherai.api-key=API_KEY",
				"spring.ai.togetherai.base-url=ENDPOINT",
				"spring.ai.togetherai.image.model=MODEL_XYZ",
				"spring.ai.togetherai.image.steps=30",
				"spring.ai.togetherai.image.image-url=https://example.com/input.png",
				"spring.ai.togetherai.image.seed=1234",
				"spring.ai.togetherai.image.n=4",
				"spring.ai.togetherai.image.height=256",
				"spring.ai.togetherai.image.width=512",
				"spring.ai.togetherai.image.negative-prompt=NEGATIVE",
				"spring.ai.togetherai.image.response-format=base64",
				"spring.ai.togetherai.image.guidance-scale=7.5",
				"spring.ai.togetherai.image.output-format=png",
				"spring.ai.togetherai.image.image-loras[0].path=lora-a",
				"spring.ai.togetherai.image.image-loras[0].scale=0.5",
				"spring.ai.togetherai.image.reference-images[0]=https://example.com/reference.png",
				"spring.ai.togetherai.image.disable-safety-checker=true"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(TogetherAiImageAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(TogetherAiConnectionProperties.class);
				var imageProperties = context.getBean(TogetherAiImageProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("ENDPOINT");

				assertThat(imageProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getSteps()).isEqualTo(30);
				assertThat(imageProperties.getImageUrl()).isEqualTo("https://example.com/input.png");
				assertThat(imageProperties.getSeed()).isEqualTo(1234L);
				assertThat(imageProperties.getN()).isEqualTo(4);
				assertThat(imageProperties.getHeight()).isEqualTo(256);
				assertThat(imageProperties.getWidth()).isEqualTo(512);
				assertThat(imageProperties.getNegativePrompt()).isEqualTo("NEGATIVE");
				assertThat(imageProperties.getResponseFormat()).isEqualTo("base64");
				assertThat(imageProperties.getGuidanceScale()).isEqualTo(7.5f);
				assertThat(imageProperties.getOutputFormat()).isEqualTo("png");
				assertThat(imageProperties.getImageLoras())
					.containsExactly(new TogetherAiImageOptions.ImageLora("lora-a", 0.5f));
				assertThat(imageProperties.getReferenceImages())
					.containsExactly("https://example.com/reference.png");
				assertThat(imageProperties.getDisableSafetyChecker()).isTrue();

				TogetherAiImageOptions imageOptions = imageProperties.toOptions();
				assertThat(imageOptions.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageOptions.getSteps()).isEqualTo(30);
				assertThat(imageOptions.getImageUrl()).isEqualTo("https://example.com/input.png");
				assertThat(imageOptions.getSeed()).isEqualTo(1234L);
				assertThat(imageOptions.getN()).isEqualTo(4);
				assertThat(imageOptions.getHeight()).isEqualTo(256);
				assertThat(imageOptions.getWidth()).isEqualTo(512);
				assertThat(imageOptions.getNegativePrompt()).isEqualTo("NEGATIVE");
				assertThat(imageOptions.getResponseFormat()).isEqualTo("base64");
				assertThat(imageOptions.getGuidanceScale()).isEqualTo(7.5f);
				assertThat(imageOptions.getOutputFormat()).isEqualTo("png");
				assertThat(imageOptions.getImageLoras())
					.containsExactly(new TogetherAiImageOptions.ImageLora("lora-a", 0.5f));
				assertThat(imageOptions.getReferenceImages()).containsExactly("https://example.com/reference.png");
				assertThat(imageOptions.getDisableSafetyChecker()).isTrue();
			});
	}

	@Test
	void togetherAiImageProperties_shouldUseDefaultBaseUrl_whenBaseUrlIsNotSet() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.togetherai.api-key=API_KEY")
			.withConfiguration(AutoConfigurations.of(TogetherAiImageAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(TogetherAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo(TogetherAiConnectionProperties.DEFAULT_BASE_URL);
			});
	}
}
