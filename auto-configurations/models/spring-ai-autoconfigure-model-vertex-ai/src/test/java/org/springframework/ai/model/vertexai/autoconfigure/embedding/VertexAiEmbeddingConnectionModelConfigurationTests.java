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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the model enablement guards of
 * {@link VertexAiEmbeddingConnectionAutoConfiguration}.
 *
 */
class VertexAiEmbeddingConnectionModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VertexAiEmbeddingConnectionAutoConfiguration.class));

	@Test
	void connectionAvailableWhenEmbeddingModelsEnabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.vertex.ai.embedding.project-id=test-project",
					"spring.ai.vertex.ai.embedding.location=us-central1")
			.run(context -> {
				assertThat(context).hasSingleBean(VertexAiEmbeddingConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

	@Test
	void connectionBacksOffWhenAllEmbeddingModelsSetToNone() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding.text=none", "spring.ai.model.embedding.multimodal=none",
					"spring.ai.vertex.ai.embedding.project-id=test-project",
					"spring.ai.vertex.ai.embedding.location=us-central1")
			.run(context -> {
				assertThat(context).doesNotHaveBean(VertexAiEmbeddingConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

	@Test
	void connectionRemainsWhenOnlyTextEmbeddingModelIsSetToNone() {
		// The multimodal embedding model is still enabled, so the shared connection must
		// stay available.
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding.text=none",
					"spring.ai.vertex.ai.embedding.project-id=test-project",
					"spring.ai.vertex.ai.embedding.location=us-central1")
			.run(context -> {
				assertThat(context).hasSingleBean(VertexAiEmbeddingConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

	@Test
	void connectionRemainsWhenOnlyMultimodalEmbeddingModelIsSetToNone() {
		// The text embedding model is still enabled, so the shared connection must stay
		// available.
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding.multimodal=none",
					"spring.ai.vertex.ai.embedding.project-id=test-project",
					"spring.ai.vertex.ai.embedding.location=us-central1")
			.run(context -> {
				assertThat(context).hasSingleBean(VertexAiEmbeddingConnectionDetails.class);
				assertThat(context).hasNotFailed();
			});
	}

}
