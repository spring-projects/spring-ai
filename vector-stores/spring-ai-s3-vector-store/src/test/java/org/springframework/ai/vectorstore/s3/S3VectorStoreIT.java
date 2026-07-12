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

package org.springframework.ai.vectorstore.s3;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for S3VectorStore.
 *
 * @author Matej Nedic
 */
class S3VectorStoreIT {

	@Test
	void testAddDocumentStoresTextInMetadata() {
		S3VectorsClient mockClient = mock(S3VectorsClient.class);
		EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);

		when(mockEmbedding.embed(any(List.class), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));
		when(mockEmbedding.dimensions()).thenReturn(3);

		S3VectorStore vectorStore = new S3VectorStore.Builder(mockClient, mockEmbedding).vectorBucketName("test-bucket")
			.indexName("test-index")
			.build();

		Document doc = new Document("test-id", "Test document content", Map.of("key1", "value1"));

		vectorStore.add(List.of(doc));

		ArgumentCaptor<PutVectorsRequest> requestCaptor = ArgumentCaptor.forClass(PutVectorsRequest.class);
		verify(mockClient).putVectors(requestCaptor.capture());

		PutVectorsRequest request = requestCaptor.getValue();
		assertThat(request.vectors()).hasSize(1);

		PutInputVector vector = request.vectors().get(0);
		assertThat(vector.key()).isEqualTo("test-id");

		software.amazon.awssdk.core.document.Document metadata = vector.metadata();
		assertThat(metadata.asMap()).containsEntry("SPRING_AI_VECTOR_CONTENT_KEY",
				software.amazon.awssdk.core.document.Document.fromString("Test document content"));
		assertThat(metadata.asMap()).containsEntry("key1",
				software.amazon.awssdk.core.document.Document.fromString("value1"));
	}

	@Test
	void testSearchReturnsDocumentText() {
		S3VectorsClient mockClient = mock(S3VectorsClient.class);
		EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);

		when(mockEmbedding.embed(any(String.class))).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(mockEmbedding.dimensions()).thenReturn(3);

		software.amazon.awssdk.core.document.Document metadata = software.amazon.awssdk.core.document.Document
			.fromMap(Map.of("SPRING_AI_VECTOR_CONTENT_KEY",
					software.amazon.awssdk.core.document.Document.fromString("Retrieved content"), "key1",
					software.amazon.awssdk.core.document.Document.fromString("value1")));

		QueryOutputVector outputVector = QueryOutputVector.builder()
			.key("doc-id")
			.metadata(metadata)
			.distance(0.95f)
			.data(VectorData.builder().float32(List.of(0.1f, 0.2f, 0.3f)).build())
			.build();

		QueryVectorsResponse response = QueryVectorsResponse.builder().vectors(List.of(outputVector)).build();

		when(mockClient.queryVectors(any(QueryVectorsRequest.class))).thenReturn(response);

		S3VectorStore vectorStore = new S3VectorStore.Builder(mockClient, mockEmbedding).vectorBucketName("test-bucket")
			.indexName("test-index")
			.build();

		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("test query").topK(1).build());

		assertThat(results).hasSize(1);
		Document result = results.get(0);
		assertThat(result.getId()).isEqualTo("doc-id");
		assertThat(result.getText()).isEqualTo("Retrieved content");
		assertThat(result.getMetadata()).containsEntry("key1", "value1");
		assertThat(result.getMetadata()).containsEntry("SPRING_AI_S3_DISTANCE", 0.95f);
		assertThat(result.getMetadata()).doesNotContainKey("SPRING_AI_VECTOR_CONTENT_KEY");
	}

	@Test
	void testSearchWithNullContentReturnsEmptyString() {
		S3VectorsClient mockClient = mock(S3VectorsClient.class);
		EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);

		when(mockEmbedding.embed(any(String.class))).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(mockEmbedding.dimensions()).thenReturn(3);

		software.amazon.awssdk.core.document.Document metadata = software.amazon.awssdk.core.document.Document
			.fromMap(Map.of("key1", software.amazon.awssdk.core.document.Document.fromString("value1")));

		QueryOutputVector outputVector = QueryOutputVector.builder()
			.key("doc-id")
			.metadata(metadata)
			.data(VectorData.builder().float32(List.of(0.1f, 0.2f, 0.3f)).build())
			.build();

		QueryVectorsResponse response = QueryVectorsResponse.builder().vectors(List.of(outputVector)).build();

		when(mockClient.queryVectors(any(QueryVectorsRequest.class))).thenReturn(response);

		S3VectorStore vectorStore = new S3VectorStore.Builder(mockClient, mockEmbedding).vectorBucketName("test-bucket")
			.indexName("test-index")
			.build();

		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("test query").topK(1).build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo("");
	}

	@Test
	void testCustomContentMetadataKeyName() {
		S3VectorsClient mockClient = mock(S3VectorsClient.class);
		EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);

		when(mockEmbedding.embed(any(List.class), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));
		when(mockEmbedding.embed(any(String.class))).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(mockEmbedding.dimensions()).thenReturn(3);

		S3VectorStore vectorStore = new S3VectorStore.Builder(mockClient, mockEmbedding).vectorBucketName("test-bucket")
			.indexName("test-index")
			.contentMetadataKeyName("content")
			.build();

		Document doc = new Document("test-id", "Custom key text", Map.of("key1", "value1"));
		vectorStore.add(List.of(doc));

		ArgumentCaptor<PutVectorsRequest> requestCaptor = ArgumentCaptor.forClass(PutVectorsRequest.class);
		verify(mockClient).putVectors(requestCaptor.capture());

		software.amazon.awssdk.core.document.Document metadata = requestCaptor.getValue().vectors().get(0).metadata();
		assertThat(metadata.asMap()).containsEntry("content",
				software.amazon.awssdk.core.document.Document.fromString("Custom key text"));

		software.amazon.awssdk.core.document.Document responseMetadata = software.amazon.awssdk.core.document.Document
			.fromMap(Map.of("content", software.amazon.awssdk.core.document.Document.fromString("Retrieved text"),
					"key1", software.amazon.awssdk.core.document.Document.fromString("value1")));

		QueryOutputVector outputVector = QueryOutputVector.builder()
			.key("doc-id")
			.metadata(responseMetadata)
			.data(VectorData.builder().float32(List.of(0.1f, 0.2f, 0.3f)).build())
			.build();

		when(mockClient.queryVectors(any(QueryVectorsRequest.class)))
			.thenReturn(QueryVectorsResponse.builder().vectors(List.of(outputVector)).build());

		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("test query").topK(1).build());

		assertThat(results.get(0).getText()).isEqualTo("Retrieved text");
		assertThat(results.get(0).getMetadata()).doesNotContainKey("content");
	}

}
