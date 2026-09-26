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

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiEmbeddingConnectionAutoConfiguration}.
 *
 * @author Hoyong Eom
 */
class GoogleGenAiEmbeddingConnectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiEmbeddingConnectionAutoConfiguration.class));

	@Test
	void connectionDetailsCreatedWhenTextEmbeddingModelIsGoogleGenAi() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.embedding.api-key=test-key",
					"spring.ai.model.embedding.text=google-genai")
			.run(context -> assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class));
	}

	@Test
	void connectionDetailsCreatedByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.google.genai.embedding.api-key=test-key")
			.run(context -> assertThat(context).hasSingleBean(GoogleGenAiEmbeddingConnectionDetails.class));
	}

	@Test
	void connectionDetailsNotCreatedWhenTextEmbeddingModelIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding.text=none")
			.run(context -> assertThat(context).doesNotHaveBean(GoogleGenAiEmbeddingConnectionDetails.class));
	}

	@Test
	void connectionDetailsNotCreatedWhenAnotherTextEmbeddingModelIsSelected() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding.text=openai")
			.run(context -> assertThat(context).doesNotHaveBean(GoogleGenAiEmbeddingConnectionDetails.class));
	}

	/**
	 * Without the property condition the auto-configuration runs unconditionally, and the
	 * unconfigured connection falls back to Vertex AI mode, failing the context with
	 * "Google GenAI project-id must be set!". Applications that do not use Google GenAI
	 * embeddings must still start.
	 */
	@Test
	void contextStartsWhenNothingIsConfiguredAndAnotherModelIsSelected() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding.text=none").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(GoogleGenAiEmbeddingConnectionDetails.class);
		});
	}

}
