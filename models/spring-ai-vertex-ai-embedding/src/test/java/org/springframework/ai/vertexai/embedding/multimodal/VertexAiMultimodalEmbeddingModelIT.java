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

package org.springframework.ai.vertexai.embedding.multimodal;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
class VertexAiMultimodalEmbeddingModelIT {

	// https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/multimodal-embeddings-api

	@Autowired
	private VertexAiMultimodalEmbeddingModel multiModelEmbeddingModel;

	@Test
	void multipleInstancesEmbedding() {

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(new Document("Hello World"),
				new Document("Hello World2"));

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(2);

		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.TEXT_PLAIN);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getDocumentId())
			.isEqualTo(embeddingRequest.getInstructions().get(0).getId());
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getResults().get(1).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(1).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.TEXT_PLAIN);
		assertThat(embeddingResponse.getResults().get(1).getMetadata().getDocumentId())
			.isEqualTo(embeddingRequest.getInstructions().get(1).getId());
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel())
			.as("Model in metadata should be 'multimodalembedding@001'")
			.isEqualTo("multimodalembedding@001");

		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens())
			.as("Total tokens in metadata should be 0")
			.isEqualTo(0L);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@Test
	void textContentEmbedding() {

		var document = new Document("Hello World");

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(document);

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.TEXT_PLAIN);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@Test
	void textMediaEmbedding() throws MalformedURLException {
		assertThat(this.multiModelEmbeddingModel).isNotNull();

		var document = Document.builder()
			.media(Media.builder()
				.mimeType(MimeTypeUtils.TEXT_PLAIN)
				.data(URI.create("http://example.com/image.png"))
				.build())
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(document);

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.TEXT_PLAIN);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@Test
	void imageEmbedding() {

		var document = Document.builder()
			.media(new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.image.png")))
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(document);

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(1);

		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.IMAGE);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.IMAGE_PNG);

		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@Test
	void videoEmbedding() {

		var document = Document.builder()
			.media(new Media(new MimeType("video", "mp4"), new ClassPathResource("/test.video.mp4")))
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(document);

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(1);

		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.VIDEO);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(new MimeType("video", "mp4"));
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@Test
	void textImageAndVideoEmbedding() {

		var textDocument = Document.builder().text("Hello World").build();

		var imageDocument = Document.builder()
			.media(new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.image.png")))
			.build();

		var videoDocument = Document.builder()
			.media(new Media(new MimeType("video", "mp4"), new ClassPathResource("/test.video.mp4")))
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(
				List.of(textDocument, imageDocument, videoDocument));

		EmbeddingResponse embeddingResponse = this.multiModelEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(3);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getResults().get(1)).isNotNull();
		assertThat(embeddingResponse.getResults().get(1).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.IMAGE);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getResults().get(2)).isNotNull();
		assertThat(embeddingResponse.getResults().get(2).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.VIDEO);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(1408);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("multimodalembedding@001");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.multiModelEmbeddingModel.dimensions()).isEqualTo(1408);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public VertexAiEmbeddingConnectionDetails connectionDetails() {
			return VertexAiEmbeddingConnectionDetails.builder()
				.projectId(System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"))
				.location(System.getenv("VERTEX_AI_GEMINI_LOCATION"))
				.build();
		}

		@Bean
		public VertexAiMultimodalEmbeddingModel vertexAiEmbeddingModel(
				VertexAiEmbeddingConnectionDetails connectionDetails) {

			VertexAiMultimodalEmbeddingOptions options = VertexAiMultimodalEmbeddingOptions.builder()
				.model(VertexAiMultimodalEmbeddingModelName.MULTIMODAL_EMBEDDING_001)
				.build();

			return new VertexAiMultimodalEmbeddingModel(connectionDetails, options);
		}

	}

}
