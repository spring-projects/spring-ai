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

package org.springframework.ai.chroma.vectorstore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChromaVectorStore}.
 *
 * @author lejuho
 */
@ExtendWith(MockitoExtension.class)
class ChromaVectorStoreTests {

	@Mock
	private ChromaApi chromaApi;

	@Mock
	private EmbeddingModel embeddingModel;

	private ChromaVectorStore vectorStore;

	@BeforeEach
	void setUp() throws Exception {
		when(this.chromaApi.getCollection(anyString(), anyString(), anyString()))
			.thenReturn(new ChromaApi.Collection("collection-1", "SpringAiTestCollection", Map.of()));
		when(this.embeddingModel.dimensions()).thenReturn(3);

		this.vectorStore = ChromaVectorStore.builder(this.chromaApi, this.embeddingModel)
			.collectionName("SpringAiTestCollection")
			.initializeSchema(false)
			.initializeImmediately(true)
			.build();
	}

	@Test
	void shouldSearchWithCallerSuppliedQueryEmbeddingWithoutEmbeddingModel() {
		ChromaApi.QueryResponse queryResponse = new ChromaApi.QueryResponse(List.of(List.of("doc-1")),
				List.of(List.of(new float[] { 0.1f, 0.2f, 0.3f })), List.of(List.of("Spring AI is great")),
				List.of(List.of(new HashMap<>(Map.of("author", "tester")))), List.of(List.of(0.05)));
		ChromaApi.Embedding embedding = new ChromaApi.Embedding("doc-1", new float[] { 0.1f, 0.2f, 0.3f },
				"Spring AI is great", new HashMap<>(Map.of("author", "tester")), 0.05);

		when(this.chromaApi.queryCollection(anyString(), anyString(), eq("collection-1"),
				any(ChromaApi.QueryRequest.class)))
			.thenReturn(queryResponse);
		when(this.chromaApi.toEmbeddingResponseList(queryResponse)).thenReturn(List.of(embedding));

		float[] queryEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

		List<Document> results = this.vectorStore.similaritySearch(queryEmbedding,
				SearchRequest.builder().topK(1).similarityThreshold(0.5).build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo("doc-1");
		assertThat(results.get(0).getText()).isEqualTo("Spring AI is great");
		verify(this.embeddingModel, never()).embed(any(String.class));
	}

	@Test
	void shouldRejectWrongWidthQueryEmbedding() {
		assertThatThrownBy(() -> this.vectorStore.similaritySearch(new float[] { 0.1f, 0.2f },
				SearchRequest.builder().topK(1).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Query embedding has dimension 2")
			.hasMessageContaining("expects dimension 3");
		verify(this.embeddingModel, never()).embed(any(String.class));
	}

}
