/*
 * Copyright 2023-2024 the original author or authors.
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

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiTextEmbeddingModelAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.ai.vertex.ai.embedding.project-id=" + System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"),
			"spring.ai.vertex.ai.embedding.location=" + System.getenv("VERTEX_AI_GEMINI_LOCATION"));

	@TempDir
	File tempDir;

	@Test
	public void textEmbedding() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiTextEmbeddingAutoConfiguration.class))
			.run(context -> {
				var conntectionProperties = context.getBean(VertexAiEmbeddingConnectionProperties.class);
				var textEmbeddingProperties = context.getBean(VertexAiTextEmbeddingProperties.class);

				assertThat(conntectionProperties).isNotNull();
				assertThat(textEmbeddingProperties.isEnabled()).isTrue();

				VertexAiTextEmbeddingModel embeddingModel = context.getBean(VertexAiTextEmbeddingModel.class);
				assertThat(embeddingModel).isInstanceOf(VertexAiTextEmbeddingModel.class);

				List<float[]> embeddings = embeddingModel.embed(List.of("Spring Framework", "Spring AI"));

				assertThat(embeddings.size()).isEqualTo(2); // batch size
				assertThat(embeddings.get(0).length).isEqualTo(embeddingModel.dimensions());
			});
	}

	@Test
	void textEmbeddingActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiTextEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.text.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiTextEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.text.embedding=vertexai")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiTextEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VertexAiTextEmbeddingModel.class)).isNotEmpty();
			});

	}

	@Test
	public void multimodalEmbedding() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiMultiModalEmbeddingAutoConfiguration.class))
			.run(context -> {
				var conntectionProperties = context.getBean(VertexAiEmbeddingConnectionProperties.class);
				var multimodalEmbeddingProperties = context.getBean(VertexAiMultimodalEmbeddingProperties.class);

				assertThat(conntectionProperties).isNotNull();
				assertThat(multimodalEmbeddingProperties.isEnabled()).isTrue();

				VertexAiMultimodalEmbeddingModel multiModelEmbeddingModel = context
					.getBean(VertexAiMultimodalEmbeddingModel.class);

				assertThat(multiModelEmbeddingModel).isNotNull();

				var document = new Document("Hello World");

				DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(List.of(document),
						EmbeddingOptionsBuilder.builder().build());

				EmbeddingResponse embeddingResponse = multiModelEmbeddingModel.call(embeddingRequest);
				assertThat(embeddingResponse.getResults()).hasSize(1);
				assertThat(embeddingResponse.getResults().get(0)).isNotNull();
				assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
					.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

				assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
				assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(0);

				assertThat(multiModelEmbeddingModel.dimensions()).isEqualTo(1408);

			});
	}

	@Test
	void multimodalEmbeddingActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiMultiModalEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.multi-modal.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiMultiModalEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.multi-modal.embedding=vertexai")
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(VertexAiMultiModalEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VertexAiMultimodalEmbeddingModel.class)).isNotEmpty();
			});

	}

}
