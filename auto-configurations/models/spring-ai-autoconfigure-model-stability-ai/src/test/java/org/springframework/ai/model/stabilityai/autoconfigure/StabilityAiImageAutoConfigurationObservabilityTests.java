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

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autoconfiguration tests that exercise the observability wiring of
 * {@link StabilityAiImageAutoConfiguration}.
 *
 * @author Gaurav Kumar
 */
class StabilityAiImageAutoConfigurationObservabilityTests {

	private static final String[] BASE_PROPS = new String[] { "spring.ai.stabilityai.image.api-key=API_KEY",
			"spring.ai.stabilityai.image.base-url=https://example.invalid" };

	@Test
	void defaultsToNoopObservationRegistryWhenNoBeanPresent() {
		new ApplicationContextRunner().withPropertyValues(BASE_PROPS)
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> assertThat(context).hasSingleBean(StabilityAiImageModel.class));
	}

	@Test
	void usesUserProvidedObservationRegistryBean() {
		new ApplicationContextRunner().withPropertyValues(BASE_PROPS)
			.withUserConfiguration(ObservationRegistryConfig.class)
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(StabilityAiImageModel.class);
				assertThat(context).hasSingleBean(ObservationRegistry.class);
			});
	}

	@Test
	void appliesUserProvidedObservationConventionBean() {
		new ApplicationContextRunner().withPropertyValues(BASE_PROPS)
			.withUserConfiguration(ObservationRegistryConfig.class, CustomConventionConfig.class)
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(StabilityAiImageModel.class);
				assertThat(context).hasSingleBean(ImageModelObservationConvention.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class ObservationRegistryConfig {

		@Bean
		ObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConventionConfig {

		@Bean
		ImageModelObservationConvention imageModelObservationConvention() {
			return new DefaultImageModelObservationConvention();
		}

	}

}
