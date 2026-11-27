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

package org.springframework.ai.voyageai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Calls the live Voyage AI API with a few small requests. Needs {@code VOYAGE_API_KEY}.
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageAiIT {

	private final VoyageAiApi api = VoyageAiApi.builder().apiKey(System.getenv("VOYAGE_API_KEY")).build();

	@Test
	void textEmbeddingsWithADimension() {
		var model = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(this.api)
			.options(VoyageAiEmbeddingOptions.builder()
				.model(VoyageAiApi.EmbeddingModel.VOYAGE_4_LITE)
				.inputType(VoyageAiApi.InputType.QUERY)
				.dimensions(256)
				.build())
			.build();

		EmbeddingResponse response = model.call(new EmbeddingRequest(
				List.of("The Mediterranean diet emphasizes fish, olive oil, and vegetables.", "two"), null));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getOutput()).hasSize(256);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@Test
	void contextualizedEmbeddingsReturnTheChunkText() {
		var model = VoyageAiContextualizedEmbeddingModel.builder()
			.voyageAiApi(this.api)
			.options(VoyageAiContextualizedEmbeddingOptions.builder()
				.model(VoyageAiApi.ContextualizedEmbeddingModel.VOYAGE_CONTEXT_4)
				.inputType(VoyageAiApi.InputType.DOCUMENT)
				.dimensions(256)
				.build())
			.build();

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("A short document."), null));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).hasSize(256);
		assertThat(response.getResults().get(0).getMetadata().getDocumentData()).isEqualTo("A short document.");
	}

	@Test
	void rerankingPutsTheRelevantDocumentFirst() {
		var reranker = VoyageAiDocumentReranker.builder()
			.voyageAiApi(this.api)
			.model(VoyageAiApi.RerankModel.RERANK_3)
			.topK(1)
			.build();

		List<Document> ranked = reranker.process(new Query("capital of France"),
				List.of(new Document("Bananas are yellow."), new Document("Paris is the capital of France.")));

		assertThat(ranked).hasSize(1);
		assertThat(ranked.get(0).getText()).startsWith("Paris");
		assertThat(ranked.get(0).getScore()).isNotNull();
	}

	@Test
	void multimodalEmbeddingsOfTextAndAnImage() {
		var model = VoyageAiMultimodalEmbeddingModel.builder()
			.voyageAiApi(this.api)
			.options(VoyageAiMultimodalEmbeddingOptions.builder()
				.model(VoyageAiApi.MultimodalEmbeddingModel.VOYAGE_MULTIMODAL_3_5)
				.dimensions(256)
				.build())
			.build();
		Document image = new Document(
				new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/voyage-test-image.png")), Map.of());

		EmbeddingResponse response = model
			.call(new DocumentEmbeddingRequest(List.of(new Document("a red square"), image), null));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(1).getOutput()).hasSize(256);
	}

	@Test
	void anInvalidApiKeyIsReportedWithTheVoyageDetail() {
		var model = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(VoyageAiApi.builder().apiKey("invalid-key").build())
			.options(VoyageAiEmbeddingOptions.builder().model(VoyageAiApi.EmbeddingModel.VOYAGE_4_LITE).build())
			.build();
		assertThatExceptionOfType(NonTransientAiException.class)
			.isThrownBy(() -> model.call(new EmbeddingRequest(List.of("x"), EmbeddingOptions.builder().build())))
			.withMessageStartingWith("401 - ");
	}

}
