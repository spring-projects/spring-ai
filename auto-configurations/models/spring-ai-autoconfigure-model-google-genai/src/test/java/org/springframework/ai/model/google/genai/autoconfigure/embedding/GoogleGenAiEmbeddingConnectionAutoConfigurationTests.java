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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link GoogleGenAiEmbeddingConnectionAutoConfiguration} does not activate
 * when no embedding credentials are present, preventing startup failures when only the
 * chat model is in use.
 *
 * @author Gorre Surya
 */
class GoogleGenAiEmbeddingConnectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
				GoogleGenAiTextEmbeddingAutoConfiguration.class, SpringAiRetryAutoConfiguration.class));

	@Test
	void noEmbeddingBeansCreatedWhenNoCredentialsConfigured() {
		this.contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(GoogleGenAiEmbeddingConnectionDetails.class);
			assertThat(context).doesNotHaveBean(GoogleGenAiTextEmbeddingModel.class);
		});
	}

	@Test
	void embeddingConnectionCreatedWhenApiKeyPresent() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.embedding.api-key=test-api-key")
			.run(context -> assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class));
	}

	@Test
	void embeddingModelNotCreatedWhenConnectionDetailsAbsent() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(GoogleGenAiTextEmbeddingModel.class));
	}

}
