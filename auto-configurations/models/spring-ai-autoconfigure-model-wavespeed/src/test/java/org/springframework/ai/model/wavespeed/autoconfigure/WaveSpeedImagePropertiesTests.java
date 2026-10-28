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

package org.springframework.ai.model.wavespeed.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.wavespeed.WaveSpeedImageModel;
import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WaveSpeedImageProperties} and {@link WaveSpeedConnectionProperties}.
 *
 * @author Zeyi Cheng
 */
class WaveSpeedImagePropertiesTests {

	@Test
	void imagePropertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.wavespeed.api-key=API_KEY",
				"spring.ai.wavespeed.base-url=ENDPOINT",
				"spring.ai.wavespeed.poll-interval=2s",
				"spring.ai.wavespeed.timeout=300s",
				"spring.ai.wavespeed.image.model=MODEL_XYZ",
				"spring.ai.wavespeed.image.size=1024*1024",
				"spring.ai.wavespeed.image.seed=42"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(WaveSpeedImageAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(WaveSpeedConnectionProperties.class);
				var imageProperties = context.getBean(WaveSpeedImageProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("ENDPOINT");
				assertThat(connectionProperties.getPollInterval()).isEqualTo(Duration.ofSeconds(2));
				assertThat(connectionProperties.getTimeout()).isEqualTo(Duration.ofSeconds(300));

				assertThat(imageProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.toOptions().getSize()).isEqualTo("1024*1024");
				assertThat(imageProperties.toOptions().getSeed()).isEqualTo(42);
			});
	}

	@Test
	void imagePropertiesDefaultsTest() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.wavespeed.api-key=API_KEY")
			.withConfiguration(AutoConfigurations.of(WaveSpeedImageAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(WaveSpeedConnectionProperties.class);
				var imageProperties = context.getBean(WaveSpeedImageProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo(WaveSpeedApi.DEFAULT_BASE_URL);
				assertThat(connectionProperties.getPollInterval()).isEqualTo(WaveSpeedApi.DEFAULT_POLL_INTERVAL);
				assertThat(connectionProperties.getTimeout()).isEqualTo(WaveSpeedApi.DEFAULT_TIMEOUT);
				assertThat(imageProperties.getModel()).isEqualTo(WaveSpeedApi.DEFAULT_IMAGE_MODEL);
				assertThat(imageProperties.getSize()).isNull();
				assertThat(imageProperties.getSeed()).isNull();
			});
	}

	@Test
	void imageActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.wavespeed.api-key=API_KEY", "spring.ai.model.image=none")
			.withConfiguration(AutoConfigurations.of(WaveSpeedImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(WaveSpeedImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(WaveSpeedImageModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.wavespeed.api-key=API_KEY", "spring.ai.model.image=wavespeed")
			.withConfiguration(AutoConfigurations.of(WaveSpeedImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(WaveSpeedImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(WaveSpeedImageModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner().withPropertyValues("spring.ai.wavespeed.api-key=API_KEY")
			.withConfiguration(AutoConfigurations.of(WaveSpeedImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(WaveSpeedImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(WaveSpeedImageModel.class)).isNotEmpty();
			});
	}

}
