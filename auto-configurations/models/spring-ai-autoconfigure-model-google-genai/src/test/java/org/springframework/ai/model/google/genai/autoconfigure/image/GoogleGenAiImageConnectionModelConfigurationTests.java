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

import org.springframework.ai.google.genai.image.GoogleGenAiImageConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the model enablement guards of
 * {@link GoogleGenAiImageConnectionAutoConfiguration}.
 *
 */
class GoogleGenAiImageConnectionModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiImageConnectionAutoConfiguration.class));

	@Test
	void connectionAvailableWhenImageModelEnabled() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.api-key=test-key").run(context -> {
			assertThat(context).hasSingleBean(GoogleGenAiImageConnectionDetails.class);
			assertThat(context).hasNotFailed();
		});
	}

	@Test
	void connectionBacksOffWhenImageModelSetToNone() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=none", "spring.ai.google.genai.api-key=test-key")
			.run(context -> {
				assertThat(context).doesNotHaveBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

	@Test
	void connectionBacksOffWhenAnotherImageModelIsSelected() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=openai", "spring.ai.google.genai.api-key=test-key")
			.run(context -> {
				assertThat(context).doesNotHaveBean(GoogleGenAiImageConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

}
