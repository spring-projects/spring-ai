/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Google GenAI auto configurations' conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
class GoogleGenAiModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void chatModelActivationWithApiKey() {

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.api-key=test-key", "spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiChatModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.api-key=test-key", "spring.ai.model.chat=google-genai")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiChatModel.class)).isNotEmpty();
			});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void chatModelActivationWithVertexAi() {

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.project-id=test-project",
					"spring.ai.google.genai.location=us-central1", "spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiChatModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.project-id=test-project",
					"spring.ai.google.genai.location=us-central1", "spring.ai.model.chat=google-genai")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiChatModel.class)).isNotEmpty();
			});
	}

	@Test
	void chatModelDefaultActivation() {
		// Tests that the model is activated by default when spring.ai.model.chat is not
		// set
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.google.genai.api-key=test-key")
			.run(context -> {
				assertThat(context.getBeansOfType(GoogleGenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(GoogleGenAiChatModel.class)).isNotEmpty();
			});
	}

}
