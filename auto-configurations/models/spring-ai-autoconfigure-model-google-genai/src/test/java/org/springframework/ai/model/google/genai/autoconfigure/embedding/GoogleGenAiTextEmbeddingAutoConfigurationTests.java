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

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GoogleGenAiTextEmbeddingAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
				GoogleGenAiTextEmbeddingAutoConfiguration.class))
		.withPropertyValues("spring.ai.google.genai.embedding.api-key=test-key");

	@Test
	void createsGoogleModelWhenNoEmbeddingModelExists() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(EmbeddingModel.class);
			assertThat(context).hasSingleBean(GoogleGenAiTextEmbeddingModel.class);
		});
	}

	@Test
	void backsOffForCustomEmbeddingModelDeclaredByInterface() {
		EmbeddingModel customModel = mock(EmbeddingModel.class);

		this.contextRunner.withBean(EmbeddingModel.class, () -> customModel).run(context -> {
			assertThat(context).hasSingleBean(EmbeddingModel.class);
			assertThat(context.getBean(EmbeddingModel.class)).isSameAs(customModel);
			assertThat(context).doesNotHaveBean(GoogleGenAiTextEmbeddingModel.class);
		});
	}

}
