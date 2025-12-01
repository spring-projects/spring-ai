/*
 * Copyright 2023-2025 the original author or authors.
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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class StabilityAiImagePropertiesTests {

	@Test
	public void chatPropertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
		"spring.ai.stabilityai.image.api-key=API_KEY",
				"spring.ai.stabilityai.image.base-url=ENDPOINT",
				"spring.ai.stabilityai.image.options.n=10",
				"spring.ai.stabilityai.image.options.model=MODEL_XYZ",
				"spring.ai.stabilityai.image.options.width=512",
				"spring.ai.stabilityai.image.options.height=256",
				"spring.ai.stabilityai.image.options.response-format=application/json",
				"spring.ai.stabilityai.image.options.n=4",
				"spring.ai.stabilityai.image.options.cfg-scale=7",
				"spring.ai.stabilityai.image.options.clip-guidance-preset=SIMPLE",
				"spring.ai.stabilityai.image.options.sampler=K_EULER",
				"spring.ai.stabilityai.image.options.seed=0",
				"spring.ai.stabilityai.image.options.steps=30",
				"spring.ai.stabilityai.image.options.style-preset=neon-punk",

				"spring.ai.stabilityai.connect-timeout=5s",
				"spring.ai.stabilityai.read-timeout=30s"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(StabilityAiImageProperties.class);
				var connectionProperties = context.getBean(StabilityAiConnectionProperties.class);

				assertThat(chatProperties.getBaseUrl()).isEqualTo("ENDPOINT");
				assertThat(chatProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getOptions().getWidth()).isEqualTo(512);
				assertThat(chatProperties.getOptions().getHeight()).isEqualTo(256);
				assertThat(chatProperties.getOptions().getResponseFormat()).isEqualTo("application/json");
				assertThat(chatProperties.getOptions().getN()).isEqualTo(4);
				assertThat(chatProperties.getOptions().getCfgScale()).isEqualTo(7);
				assertThat(chatProperties.getOptions().getClipGuidancePreset()).isEqualTo("SIMPLE");
				assertThat(chatProperties.getOptions().getSampler()).isEqualTo("K_EULER");
				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getSteps()).isEqualTo(30);
				assertThat(chatProperties.getOptions().getStylePreset()).isEqualTo("neon-punk");

				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
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
