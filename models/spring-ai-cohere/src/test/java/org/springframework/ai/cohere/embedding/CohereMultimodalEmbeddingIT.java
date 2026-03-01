/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.cohere.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.cohere.CohereTestConfiguration;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata.ModalityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = CohereTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereMultimodalEmbeddingIT {

	private static final int EMBED_DIMENSIONS = 1536;

	@Autowired
	private CohereApi cohereApi;

	@Autowired
	private CohereMultimodalEmbeddingModel cohereMultimodalEmbeddingModel;

	@Test
	void imageEmbedding() {

		var document = Document.builder()
			.media(new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.image.png")))
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(document);

		EmbeddingResponse embeddingResponse = this.cohereMultimodalEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(1);

		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType()).isEqualTo(ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getMimeType())
			.isEqualTo(MimeTypeUtils.TEXT_PLAIN);

		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(EMBED_DIMENSIONS);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("embeddings_by_type");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.cohereMultimodalEmbeddingModel.dimensions()).isEqualTo(EMBED_DIMENSIONS);
	}

	@Test
	void textAndImageEmbedding() {

		var textDocument = Document.builder().text("Hello World").build();

		var imageDocument = Document.builder()
			.media(new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.image.png")))
			.build();

		DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(List.of(textDocument, imageDocument));

		EmbeddingResponse embeddingResponse = this.cohereMultimodalEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getMetadata().getModalityType())
			.isEqualTo(EmbeddingResultMetadata.ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(EMBED_DIMENSIONS);

		assertThat(embeddingResponse.getResults().get(1)).isNotNull();
		assertThat(embeddingResponse.getResults().get(1).getMetadata().getModalityType()).isEqualTo(ModalityType.TEXT);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(EMBED_DIMENSIONS);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("embeddings_by_type");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(0);

		assertThat(this.cohereMultimodalEmbeddingModel.dimensions()).isEqualTo(EMBED_DIMENSIONS);
	}

}
