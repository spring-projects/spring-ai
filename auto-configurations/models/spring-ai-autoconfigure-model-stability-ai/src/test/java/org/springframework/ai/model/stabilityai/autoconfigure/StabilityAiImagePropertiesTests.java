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

package org.springframework.ai.model.stabilityai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @since 0.8.0
 */
public class StabilityAiImagePropertiesTests {

	@Test
	public void chatPropertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
		"spring.ai.stabilityai.image.api-key=API_KEY",
				"spring.ai.stabilityai.image.base-url=ENDPOINT",
				"spring.ai.stabilityai.image.n=10",
				"spring.ai.stabilityai.image.model=MODEL_XYZ",
				"spring.ai.stabilityai.image.width=512",
				"spring.ai.stabilityai.image.height=256",
				"spring.ai.stabilityai.image.response-format=application/json",
				"spring.ai.stabilityai.image.n=4",
				"spring.ai.stabilityai.image.cfg-scale=7",
				"spring.ai.stabilityai.image.clip-guidance-preset=SIMPLE",
				"spring.ai.stabilityai.image.sampler=K_EULER",
				"spring.ai.stabilityai.image.seed=0",
				"spring.ai.stabilityai.image.steps=30",
				"spring.ai.stabilityai.image.style-preset=neon-punk"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(StabilityAiImageProperties.class);

				assertThat(chatProperties.getBaseUrl()).isEqualTo("ENDPOINT");
				assertThat(chatProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(chatProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.toOptions().getWidth()).isEqualTo(512);
				assertThat(chatProperties.toOptions().getHeight()).isEqualTo(256);
				assertThat(chatProperties.toOptions().getResponseFormat()).isEqualTo("application/json");
				assertThat(chatProperties.toOptions().getN()).isEqualTo(4);
				assertThat(chatProperties.toOptions().getCfgScale()).isEqualTo(7);
				assertThat(chatProperties.toOptions().getClipGuidancePreset()).isEqualTo("SIMPLE");
				assertThat(chatProperties.toOptions().getSampler()).isEqualTo("K_EULER");
				assertThat(chatProperties.toOptions().getSeed()).isEqualTo(0L);
				assertThat(chatProperties.toOptions().getSteps()).isEqualTo(30);
				assertThat(chatProperties.toOptions().getStylePreset()).isEqualTo("neon-punk");
			});
	}

	@Test
	void stabilityImageActivation() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.stabilityai.image.api-key=API_KEY",
					"spring.ai.stabilityai.image.base-url=ENDPOINT", "spring.ai.model.image=none")
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(StabilityAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(StabilityAiImageModel.class)).isEmpty();

			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.stabilityai.image.api-key=API_KEY",
					"spring.ai.stabilityai.image.base-url=ENDPOINT")
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(StabilityAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(StabilityAiImageModel.class)).isNotEmpty();

			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.stabilityai.image.api-key=API_KEY",
					"spring.ai.stabilityai.image.base-url=ENDPOINT", "spring.ai.model.image=stabilityai")
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(StabilityAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(StabilityAiImageModel.class)).isNotEmpty();

			});

	}

}
