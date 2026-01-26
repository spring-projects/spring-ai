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

package org.springframework.ai.vectorstore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleVectorStoreTests {

	@TempDir(cleanup = CleanupMode.ON_SUCCESS)
	Path tempDir;

	private SimpleVectorStore vectorStore;

	private EmbeddingModel mockEmbeddingModel;

	@BeforeEach
	void setUp() {
		this.mockEmbeddingModel = mock(EmbeddingModel.class);
		when(this.mockEmbeddingModel.dimensions()).thenReturn(3);
		when(this.mockEmbeddingModel.embed(any(String.class))).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(this.mockEmbeddingModel.embed(any(Document.class))).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		this.vectorStore = new SimpleVectorStore(SimpleVectorStore.builder(this.mockEmbeddingModel));
	}

	@Test
	void shouldAddAndRetrieveDocument() {
		Document doc = Document.builder().id("1").text("test content").metadata(Map.of("key", "value")).build();

		this.vectorStore.add(List.of(doc));

		List<Document> results = this.vectorStore.similaritySearch("test content");
		assertThat(results).hasSize(1).first().satisfies(result -> {
			assertThat(result.getId()).isEqualTo("1");
			assertThat(result.getText()).isEqualTo("test content");
			assertThat(result.getMetadata()).containsEntry("key", "value");
		});
	}

	@Test
	void shouldAddMultipleDocuments() {
		List<Document> docs = Arrays.asList(Document.builder().id("1").text("first").build(),
				Document.builder().id("2").text("second").build());

		this.vectorStore.add(docs);

		List<Document> results = this.vectorStore.similaritySearch("first");
		assertThat(results).hasSize(2).extracting(Document::getId).containsExactlyInAnyOrder("1", "2");
	}

	@Test
	void shouldHandleEmptyDocumentList() {
		assertThatThrownBy(() -> this.vectorStore.add(Collections.emptyList()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Documents list cannot be empty");
	}

	@Test
	void shouldHandleNullDocumentList() {
		assertThatThrownBy(() -> this.vectorStore.add(null)).isInstanceOf(NullPointerException.class)
			.hasMessage("Documents list cannot be null");
	}

	@Test
	void shouldDeleteDocuments() {
		Document doc = Document.builder().id("1").text("test content").build();

		this.vectorStore.add(List.of(doc));
		assertThat(this.vectorStore.similaritySearch("test")).hasSize(1);

		this.vectorStore.delete(List.of("1"));
		assertThat(this.vectorStore.similaritySearch("test")).isEmpty();
	}

	@Test
	void shouldDeleteDocumentsByFilter() {
		Document doc = Document.builder().id("1").text("test content").metadata("testKey", 1).build();

		this.vectorStore.add(List.of(doc));
		assertThat(this.vectorStore.similaritySearch("test")).hasSize(1);

		FilterExpressionBuilder builder = new FilterExpressionBuilder();

		Filter.Expression condition = builder.eq("testKey", 1).build();

		this.vectorStore.delete(condition);
		assertThat(this.vectorStore.store.isEmpty());
	}

	@Test
	void shouldHandleDeleteOfNonexistentDocument() {
		this.vectorStore.delete(List.of("nonexistent-id"));
		// Should not throw exception
		assertDoesNotThrow(() -> this.vectorStore.delete(List.of("nonexistent-id")));
	}

	@Test
	void shouldPerformSimilaritySearchWithThreshold() {
		// Configure mock to return different embeddings for different queries
		when(this.mockEmbeddingModel.embed("query")).thenReturn(new float[] { 0.9f, 0.9f, 0.9f });

		Document doc = Document.builder().id("1").text("test content").build();

		this.vectorStore.add(List.of(doc));

		SearchRequest request = SearchRequest.builder().query("query").similarityThreshold(0.99f).topK(5).build();

		List<Document> results = this.vectorStore.similaritySearch(request);
		assertThat(results).isEmpty();
	}

	@Test
	void shouldSaveAndLoadVectorStore() throws IOException {
		Document doc = Document.builder()
			.id("1")
			.text("test content")
			.metadata(new HashMap<>(Map.of("key", "value")))
			.build();

		this.vectorStore.add(List.of(doc));

		File saveFile = this.tempDir.resolve("vector-store.json").toFile();
		this.vectorStore.save(saveFile);

		SimpleVectorStore loadedStore = SimpleVectorStore.builder(this.mockEmbeddingModel).build();
		loadedStore.load(saveFile);

		List<Document> results = loadedStore.similaritySearch("test content");
		assertThat(results).hasSize(1).first().satisfies(result -> {
			assertThat(result.getId()).isEqualTo("1");
			assertThat(result.getText()).isEqualTo("test content");
			assertThat(result.getMetadata()).containsEntry("key", "value");
		});
	}

	@Test
	void shouldHandleLoadFromInvalidResource() throws IOException {
		Resource mockResource = mock(Resource.class);
		when(mockResource.getInputStream()).thenThrow(new IOException("Resource not found"));

		assertThatThrownBy(() -> this.vectorStore.load(mockResource)).isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(IOException.class)
			.hasMessageContaining("Resource not found");
	}

	@Test
	void shouldHandleSaveToInvalidLocation() {
		File invalidFile = new File("/invalid/path/file.json");

		assertThatThrownBy(() -> this.vectorStore.save(invalidFile)).isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void shouldHandleConcurrentOperations() throws InterruptedException {
		int numThreads = 10;
		Thread[] threads = new Thread[numThreads];

		for (int i = 0; i < numThreads; i++) {
			final String id = String.valueOf(i);
			threads[i] = new Thread(() -> {
				Document doc = Document.builder().id(id).text("content " + id).build();
				this.vectorStore.add(List.of(doc));
			});
			threads[i].start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		SearchRequest request = SearchRequest.builder().query("test").topK(numThreads).build();

		List<Document> results = this.vectorStore.similaritySearch(request);

		assertThat(results).hasSize(numThreads);

		// Verify all documents were properly added
		Set<String> resultIds = results.stream().map(Document::getId).collect(Collectors.toSet());

		Set<String> expectedIds = new java.util.HashSet<>();
		for (int i = 0; i < numThreads; i++) {
			expectedIds.add(String.valueOf(i));
		}

		assertThat(resultIds).containsExactlyInAnyOrderElementsOf(expectedIds);

		// Verify content integrity
		results.forEach(doc -> assertThat(doc.getText()).isEqualTo("content " + doc.getId()));
	}

	@Test
	void shouldRejectInvalidSimilarityThreshold() {
		assertThatThrownBy(() -> SearchRequest.builder().query("test").similarityThreshold(2.0f).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Similarity threshold must be in [0,1] range.");
	}

	@Test
	void shouldRejectNegativeTopK() {
		assertThatThrownBy(() -> SearchRequest.builder().query("test").topK(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("TopK should be positive.");
	}

	@Test
	void shouldHandleCosineSimilarityEdgeCases() {
		float[] zeroVector = new float[] { 0f, 0f, 0f };
		float[] normalVector = new float[] { 1f, 1f, 1f };

		assertThatThrownBy(() -> SimpleVectorStore.EmbeddingMath.cosineSimilarity(zeroVector, normalVector))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Vectors cannot have zero norm");
	}

	@Test
	void shouldHandleVectorLengthMismatch() {
		float[] vector1 = new float[] { 1f, 2f };
		float[] vector2 = new float[] { 1f, 2f, 3f };

		assertThatThrownBy(() -> SimpleVectorStore.EmbeddingMath.cosineSimilarity(vector1, vector2))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Vectors lengths must be equal");
	}

	@Test
	void shouldHandleNullVectors() {
		float[] vector = new float[] { 1f, 2f, 3f };

		assertThatThrownBy(() -> SimpleVectorStore.EmbeddingMath.cosineSimilarity(null, vector))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("Vectors must not be null");

		assertThatThrownBy(() -> SimpleVectorStore.EmbeddingMath.cosineSimilarity(vector, null))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("Vectors must not be null");
	}

	@Test
	void shouldFailNonTextDocuments() {
		Media media = new Media(MimeType.valueOf("image/png"), new ByteArrayResource(new byte[] { 0x00 }));

		Document imgDoc = Document.builder().media(media).metadata(Map.of("fileName", "pixel.png")).build();

		Exception exception = assertThrows(IllegalArgumentException.class, () -> this.vectorStore.add(List.of(imgDoc)));
		assertEquals("Only text documents are supported for now. One of the documents contains non-text content.",
				exception.getMessage());
	}

	@Test
	void shouldHandleDocumentWithoutId() {
		Document doc = Document.builder().text("content without id").build();

		this.vectorStore.add(List.of(doc));

		List<Document> results = this.vectorStore.similaritySearch("content");
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isNotEmpty();
	}

	@Test
	void shouldHandleDocumentWithEmptyText() {
		Document doc = Document.builder().id("1").text("").build();

		assertDoesNotThrow(() -> this.vectorStore.add(List.of(doc)));

		List<Document> results = this.vectorStore.similaritySearch("anything");
		assertThat(results).hasSize(1);
	}

	@Test
	void shouldReplaceDocumentWithSameId() {
		Document doc1 = Document.builder().id("1").text("original").metadata(Map.of("version", "1")).build();
		Document doc2 = Document.builder().id("1").text("updated").metadata(Map.of("version", "2")).build();

		this.vectorStore.add(List.of(doc1));
		this.vectorStore.add(List.of(doc2));

		List<Document> results = this.vectorStore.similaritySearch("updated");
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo("updated");
		assertThat(results.get(0).getMetadata()).containsEntry("version", "2");
	}

	@Test
	void shouldHandleSearchWithEmptyQuery() {
		Document doc = Document.builder().id("1").text("content").build();
		this.vectorStore.add(List.of(doc));

		List<Document> results = this.vectorStore.similaritySearch("");
		assertThat(results).hasSize(1);
	}

}
