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

package org.springframework.ai.voyageai.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.voyageai.api.VoyageAiApi.ContentPart;
import org.springframework.ai.voyageai.api.VoyageAiApi.ContextualizedEmbeddingRequest;
import org.springframework.ai.voyageai.api.VoyageAiApi.EmbeddingRequest;
import org.springframework.ai.voyageai.api.VoyageAiApi.InputType;
import org.springframework.ai.voyageai.api.VoyageAiApi.MultimodalEmbeddingRequest;
import org.springframework.ai.voyageai.api.VoyageAiApi.MultimodalInput;
import org.springframework.ai.voyageai.api.VoyageAiApi.RerankRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests that the requests reject what Voyage AI documents as invalid before anything is
 * sent.
 */
class VoyageAiApiTests {

	@Test
	void embeddingRequestRequiresAKnownTextEmbeddingModel() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingRequest(List.of("a"), "", null, null, null))
			.withMessageContaining("no default model");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "text-embedding-3-small", null, null, null))
			.withMessageContaining("is not a Voyage AI text embedding model");
		// a model of another Voyage AI endpoint is not a text embedding model
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "voyage-context-4", null, null, null))
			.withMessageContaining("is not a Voyage AI text embedding model");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "rerank-3", null, null, null))
			.withMessageContaining("is not a Voyage AI text embedding model");
	}

	@Test
	void embeddingRequestChecksTheOutputDimensionAgainstTheModel() {
		assertThatNoException().isThrownBy(() -> new EmbeddingRequest(List.of("a"), "voyage-4", null, null, 2048));
		assertThatNoException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "voyage-finance-2", null, null, 1024));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "voyage-law-2", null, null, 512))
			.withMessageContaining("supports the output dimensions [1024]");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of("a"), "voyage-4-lite", null, null, 300));
	}

	@Test
	void embeddingRequestHoldsBetweenOneAndAThousandInputs() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(List.of(), "voyage-4", null, null, null));
		List<String> tooMany = java.util.Collections.nCopies(1001, "a");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingRequest(tooMany, "voyage-4", null, null, null))
			.withMessageContaining("at most 1000 inputs");
		assertThatNoException().isThrownBy(
				() -> new EmbeddingRequest(java.util.Collections.nCopies(1000, "a"), "voyage-4", null, null, null));
	}

	@Test
	void contextualizedRequestRejectsPreChunkedDocumentsWithAutoChunking() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of(List.of("chunk 1", "chunk 2")),
					"voyage-context-4", InputType.DOCUMENT, null, true, 32_000))
			.withMessageContaining("one string per document");
	}

	@Test
	void contextualizedRequestAutoChunksOnlyDocuments() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of("a query"), "voyage-context-4",
					InputType.QUERY, null, true, 32_000))
			.withMessageContaining("only for document inputs");
	}

	@Test
	void contextualizedRequestNeedsAnInputType() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new ContextualizedEmbeddingRequest(List.of("doc"), "voyage-context-4", null, null, true, 32_000))
			.withMessageContaining("inputType must not be null");
	}

	@Test
	void contextualizedRequestChecksTheChunkSize() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of("doc"), "voyage-context-4", InputType.DOCUMENT,
					null, true, 32_001))
			.withMessageContaining("chunk_size must be between 1 and 32000");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of(List.of("a")), "voyage-context-4",
					InputType.DOCUMENT, null, null, 512))
			.withMessageContaining("only valid with auto-chunking");
	}

	@Test
	void contextualizedRequestWithoutAutoChunkingNeedsChunkListsForDocuments() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of("a whole document"), "voyage-context-4",
					InputType.DOCUMENT, null, null, null))
			.withMessageContaining("must be lists of chunks");
		assertThatNoException().isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of(List.of("chunk")),
				"voyage-context-4", InputType.DOCUMENT, null, null, null));
		assertThatNoException().isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of(List.of("query")),
				"voyage-context-3", InputType.QUERY, 256, null, null));
	}

	@Test
	void contextualizedRequestRejectsModelsOfOtherEndpoints() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ContextualizedEmbeddingRequest(List.of("doc"), "voyage-4", InputType.DOCUMENT, null,
					true, 32_000))
			.withMessageContaining("is not a Voyage AI contextualized embedding model");
	}

	@Test
	void multimodalRequestRejectsMixingUrlsAndBase64() {
		var url = new MultimodalInput(List.of(ContentPart.imageUrl("https://example.com/a.png")));
		var base64 = new MultimodalInput(List.of(ContentPart.imageBase64("data:image/png;base64,AAAA")));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new MultimodalEmbeddingRequest(List.of(url, base64), "voyage-multimodal-3.5", null, null, null))
			.withMessageContaining("either all as URLs or all as base64");
		assertThatNoException().isThrownBy(() -> new MultimodalEmbeddingRequest(
				List.of(url, new MultimodalInput(List.of(ContentPart.text("text")))), "voyage-multimodal-3.5", null,
				null, null));
	}

	@Test
	void multimodalRequestChecksModelCapabilities() {
		var video = new MultimodalInput(List.of(ContentPart.videoUrl("https://example.com/a.mp4")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MultimodalEmbeddingRequest(List.of(video), "voyage-multimodal-3", null, null, null))
			.withMessageContaining("does not support video");
		var text = new MultimodalInput(List.of(ContentPart.text("hi")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MultimodalEmbeddingRequest(List.of(text), "voyage-multimodal-3", null, null, 256))
			.withMessageContaining("supports the output dimensions [1024]");
		assertThatNoException().isThrownBy(
				() -> new MultimodalEmbeddingRequest(List.of(video), "voyage-multimodal-3.5", null, null, 2048));
	}

	@Test
	void rerankRequestChecksModelQueryAndTopK() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RerankRequest("q", List.of("d"), "", null, null, null))
			.withMessageContaining("no default model");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RerankRequest("q", List.of("d"), "voyage-4", null, null, null))
			.withMessageContaining("is not a Voyage AI rerank model");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RerankRequest(" ", List.of("d"), "rerank-3", null, null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RerankRequest("q", List.of("d"), "rerank-3", 0, null, null));
		assertThatNoException().isThrownBy(() -> new RerankRequest("q", List.of("d"), "rerank-3-lite", 1, true, null));
	}

	@Test
	void everyModelNameResolvesToItself() {
		for (var model : VoyageAiApi.EmbeddingModel.values()) {
			assertThat(VoyageAiApi.EmbeddingModel.fromValue(model.getValue())).isEqualTo(model);
		}
		for (var model : VoyageAiApi.RerankModel.values()) {
			assertThat(VoyageAiApi.RerankModel.fromValue(model.getValue())).isEqualTo(model);
		}
	}

}
