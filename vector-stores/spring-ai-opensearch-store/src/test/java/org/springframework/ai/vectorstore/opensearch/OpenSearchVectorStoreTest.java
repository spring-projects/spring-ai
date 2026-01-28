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

package org.springframework.ai.vectorstore.opensearch;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OpenSearchVectorStore.doAdd() method.
 *
 * Focuses on testing the manageDocumentIds functionality and document ID handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenSearchVectorStore.doAdd() Tests")
class OpenSearchVectorStoreTest {

	@Mock
	private OpenSearchClient mockOpenSearchClient;

	@Mock
	private EmbeddingModel mockEmbeddingModel;

	@Mock
	private BulkResponse mockBulkResponse;

	@BeforeEach
	void setUp() throws IOException {
		// Use lenient to avoid UnnecessaryStubbingException
		lenient().when(this.mockEmbeddingModel.dimensions()).thenReturn(3);
		lenient().when(this.mockOpenSearchClient.bulk(any(BulkRequest.class))).thenReturn(this.mockBulkResponse);
		lenient().when(this.mockBulkResponse.errors()).thenReturn(false);
	}

	@ParameterizedTest(name = "manageDocumentIds={0}")
	@ValueSource(booleans = { true, false })
	@DisplayName("Should handle document ID management setting correctly")
	void shouldHandleDocumentIdManagementSetting(boolean manageDocumentIds) throws IOException {
		// Given
		when(this.mockEmbeddingModel.embed(any(), any(), any()))
			.thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }, new float[] { 0.4f, 0.5f, 0.6f }));

		OpenSearchVectorStore vectorStore = createVectorStore(manageDocumentIds);
		List<Document> documents = List.of(new Document("doc1", "content1", Map.of()),
				new Document("doc2", "content2", Map.of()));

		// When
		vectorStore.add(documents);

		// Then
		BulkRequest capturedRequest = captureBulkRequest();
		assertThat(capturedRequest.operations()).hasSize(2);

		verifyDocumentIdHandling(capturedRequest, manageDocumentIds);
	}

	@Test
	@DisplayName("Should handle single document correctly")
	void shouldHandleSingleDocumentCorrectly() throws IOException {
		// Given
		when(this.mockEmbeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));

		OpenSearchVectorStore vectorStore = createVectorStore(true);
		Document document = new Document("test-id", "test content", Map.of("key", "value"));

		// When
		vectorStore.add(List.of(document));

		// Then
		BulkRequest capturedRequest = captureBulkRequest();
		var operation = capturedRequest.operations().get(0);

		assertThat(operation.isIndex()).isTrue();
		assertThat(operation.index().id()).isEqualTo("test-id");
		assertThat(operation.index().document()).isNotNull();
	}

	@Test
	@DisplayName("Should handle multiple documents with explicit IDs")
	void shouldHandleMultipleDocumentsWithExplicitIds() throws IOException {
		// Given
		when(this.mockEmbeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f },
				new float[] { 0.4f, 0.5f, 0.6f }, new float[] { 0.7f, 0.8f, 0.9f }));

		OpenSearchVectorStore vectorStore = createVectorStore(true);
		List<Document> documents = List.of(new Document("doc1", "content1", Map.of()),
				new Document("doc2", "content2", Map.of()), new Document("doc3", "content3", Map.of()));

		// When
		vectorStore.add(documents);

		// Then
		BulkRequest capturedRequest = captureBulkRequest();
		assertThat(capturedRequest.operations()).hasSize(3);

		for (int i = 0; i < 3; i++) {
			var operation = capturedRequest.operations().get(i);
			assertThat(operation.isIndex()).isTrue();
			assertThat(operation.index().id()).isEqualTo("doc" + (i + 1));
		}
	}

	@Test
	@DisplayName("Should handle multiple documents without explicit IDs")
	void shouldHandleMultipleDocumentsWithoutExplicitIds() throws IOException {
		// Given
		when(this.mockEmbeddingModel.embed(any(), any(), any()))
			.thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }, new float[] { 0.4f, 0.5f, 0.6f }));

		OpenSearchVectorStore vectorStore = createVectorStore(false);
		List<Document> documents = List.of(new Document("doc1", "content1", Map.of()),
				new Document("doc2", "content2", Map.of()));

		// When
		vectorStore.add(documents);

		// Then
		BulkRequest capturedRequest = captureBulkRequest();
		assertThat(capturedRequest.operations()).hasSize(2);

		for (var operation : capturedRequest.operations()) {
			assertThat(operation.isIndex()).isTrue();
			assertThat(operation.index().id()).isNull();
		}
	}

	@Test
	@DisplayName("Should handle embedding model error")
	void shouldHandleEmbeddingModelError() {
		// Given
		when(this.mockEmbeddingModel.embed(any(), any(), any())).thenThrow(new RuntimeException("Embedding failed"));

		OpenSearchVectorStore vectorStore = createVectorStore(true);
		List<Document> documents = List.of(new Document("doc1", "content", Map.of()));

		// When & Then
		assertThatThrownBy(() -> vectorStore.add(documents)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Embedding failed");
	}

	// Helper methods

	private OpenSearchVectorStore createVectorStore(boolean manageDocumentIds) {
		return OpenSearchVectorStore.builder(this.mockOpenSearchClient, this.mockEmbeddingModel)
			.manageDocumentIds(manageDocumentIds)
			.build();
	}

	private BulkRequest captureBulkRequest() throws IOException {
		ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
		verify(this.mockOpenSearchClient).bulk(captor.capture());
		return captor.getValue();
	}

	private void verifyDocumentIdHandling(BulkRequest request, boolean shouldHaveExplicitIds) {
		for (int i = 0; i < request.operations().size(); i++) {
			var operation = request.operations().get(i);
			assertThat(operation.isIndex()).isTrue();

			if (shouldHaveExplicitIds) {
				assertThat(operation.index().id()).isEqualTo("doc" + (i + 1));
			}
			else {
				assertThat(operation.index().id()).isNull();
			}
		}
	}

}
