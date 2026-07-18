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

import org.springframework.ai.togetherai.TogetherAiImageModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TogetherAiAutoConfigurationIT {
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.togetherai.api-key=API_KEY", "spring.ai.togetherai.base-url=ENDPOINT");

	@Test
	void imageBeans_shouldNotBeRegistered_whenGlobalImageModelIsDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=none")
			.withConfiguration(AutoConfigurations.of(TogetherAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(TogetherAiImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(TogetherAiImageModel.class)).isEmpty();
			});
	}

	@Test
	void imageBeans_shouldBeRegistered_whenGlobalImageModelIsNotSet() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(TogetherAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(TogetherAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(TogetherAiImageModel.class)).isNotEmpty();
			});
	}

	@Test
	void imageBeans_shouldBeRegistered_whenGlobalImageModelIsTogetherAi() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=togetherai")
			.withConfiguration(AutoConfigurations.of(TogetherAiImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(TogetherAiImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(TogetherAiImageModel.class)).isNotEmpty();
			});
	}
}
