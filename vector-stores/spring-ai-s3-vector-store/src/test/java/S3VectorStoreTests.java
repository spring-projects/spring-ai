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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListOutputVector;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.s3.DocumentUtils;
import org.springframework.ai.vectorstore.s3.S3VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jewoo Shin
 */
class S3VectorStoreTests {

	@Test
	void filterDeleteListsVectorsWithMetadataAndDeletesMatchingKeys() {
		S3VectorsClient s3VectorsClient = mock(S3VectorsClient.class);
		when(s3VectorsClient.listVectors(any(ListVectorsRequest.class))).thenReturn(ListVectorsResponse.builder()
			.vectors(vector("chunk-1", Map.of("fileId", "file-1", "page", 1)),
					vector("chunk-2", Map.of("fileId", "file-2", "page", 1)),
					vector("chunk-3", Map.of("fileId", "file-1", "page", 2)))
			.build());

		S3VectorStore vectorStore = vectorStore(s3VectorsClient);

		vectorStore.delete(new FilterExpressionBuilder().eq("fileId", "file-1").build());

		ArgumentCaptor<ListVectorsRequest> listRequestCaptor = ArgumentCaptor.forClass(ListVectorsRequest.class);
		verify(s3VectorsClient).listVectors(listRequestCaptor.capture());
		assertThat(listRequestCaptor.getValue().vectorBucketName()).isEqualTo("test-vector-bucket");
		assertThat(listRequestCaptor.getValue().indexName()).isEqualTo("test-index");
		assertThat(listRequestCaptor.getValue().returnMetadata()).isTrue();
		assertThat(listRequestCaptor.getValue().returnData()).isFalse();
		assertThat(listRequestCaptor.getValue().maxResults()).isEqualTo(500);

		ArgumentCaptor<DeleteVectorsRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteVectorsRequest.class);
		verify(s3VectorsClient).deleteVectors(deleteRequestCaptor.capture());
		assertThat(deleteRequestCaptor.getValue().keys()).containsExactly("chunk-1", "chunk-3");
		assertThat(deleteRequestCaptor.getValue().vectorBucketName()).isEqualTo("test-vector-bucket");
		assertThat(deleteRequestCaptor.getValue().indexName()).isEqualTo("test-index");

		verify(s3VectorsClient, never()).queryVectors(any(QueryVectorsRequest.class));
	}

	@Test
	void filterDeleteEvaluatesCompoundFilterExpressions() {
		S3VectorsClient s3VectorsClient = mock(S3VectorsClient.class);
		when(s3VectorsClient.listVectors(any(ListVectorsRequest.class))).thenReturn(ListVectorsResponse.builder()
			.vectors(vector("chunk-1", Map.of("fileId", "file-1", "page", 1)),
					vector("chunk-2", Map.of("fileId", "file-1", "page", 2)),
					vector("chunk-3", Map.of("fileId", "file-2", "page", 3)))
			.build());
		FilterExpressionBuilder builder = new FilterExpressionBuilder();

		S3VectorStore vectorStore = vectorStore(s3VectorsClient);

		vectorStore.delete(builder.and(builder.eq("fileId", "file-1"), builder.gte("page", 2)).build());

		ArgumentCaptor<DeleteVectorsRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteVectorsRequest.class);
		verify(s3VectorsClient).deleteVectors(deleteRequestCaptor.capture());
		assertThat(deleteRequestCaptor.getValue().keys()).containsExactly("chunk-2");
	}

	@Test
	void filterDeleteScansAllListVectorsPages() {
		S3VectorsClient s3VectorsClient = mock(S3VectorsClient.class);
		when(s3VectorsClient.listVectors(any(ListVectorsRequest.class))).thenReturn(
				ListVectorsResponse.builder()
					.vectors(vector("chunk-1", Map.of("fileId", "file-1")))
					.nextToken("next-page")
					.build(),
				ListVectorsResponse.builder().vectors(vector("chunk-2", Map.of("fileId", "file-1"))).build());

		S3VectorStore vectorStore = vectorStore(s3VectorsClient);

		vectorStore.delete(new FilterExpressionBuilder().eq("fileId", "file-1").build());

		ArgumentCaptor<ListVectorsRequest> listRequestCaptor = ArgumentCaptor.forClass(ListVectorsRequest.class);
		verify(s3VectorsClient, times(2)).listVectors(listRequestCaptor.capture());
		assertThat(listRequestCaptor.getAllValues()).extracting(ListVectorsRequest::nextToken)
			.containsExactly(null, "next-page");

		ArgumentCaptor<DeleteVectorsRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteVectorsRequest.class);
		verify(s3VectorsClient).deleteVectors(deleteRequestCaptor.capture());
		assertThat(deleteRequestCaptor.getValue().keys()).containsExactly("chunk-1", "chunk-2");
	}

	@Test
	void filterDeleteSplitsDeleteRequestsIntoBatches() {
		S3VectorsClient s3VectorsClient = mock(S3VectorsClient.class);
		List<ListOutputVector> vectors = IntStream.rangeClosed(1, 501)
			.mapToObj(index -> vector("chunk-" + index, Map.of("fileId", "file-1")))
			.toList();
		when(s3VectorsClient.listVectors(any(ListVectorsRequest.class)))
			.thenReturn(ListVectorsResponse.builder().vectors(vectors).build());

		S3VectorStore vectorStore = vectorStore(s3VectorsClient);

		vectorStore.delete(new FilterExpressionBuilder().eq("fileId", "file-1").build());

		ArgumentCaptor<DeleteVectorsRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteVectorsRequest.class);
		verify(s3VectorsClient, times(2)).deleteVectors(deleteRequestCaptor.capture());
		assertThat(deleteRequestCaptor.getAllValues().get(0).keys()).hasSize(500);
		assertThat(deleteRequestCaptor.getAllValues().get(1).keys()).containsExactly("chunk-501");
	}

	@Test
	void filterDeleteSkipsDeleteWhenNoVectorsMatch() {
		S3VectorsClient s3VectorsClient = mock(S3VectorsClient.class);
		when(s3VectorsClient.listVectors(any(ListVectorsRequest.class)))
			.thenReturn(ListVectorsResponse.builder().vectors(vector("chunk-1", Map.of("fileId", "file-2"))).build());

		S3VectorStore vectorStore = vectorStore(s3VectorsClient);

		vectorStore.delete(new FilterExpressionBuilder().eq("fileId", "file-1").build());

		verify(s3VectorsClient, never()).deleteVectors(any(DeleteVectorsRequest.class));
		verify(s3VectorsClient, never()).queryVectors(any(QueryVectorsRequest.class));
	}

	private static S3VectorStore vectorStore(S3VectorsClient s3VectorsClient) {
		return new S3VectorStore.Builder(s3VectorsClient, mock(EmbeddingModel.class))
			.vectorBucketName("test-vector-bucket")
			.indexName("test-index")
			.build();
	}

	private static ListOutputVector vector(String key, Map<String, Object> metadata) {
		return ListOutputVector.builder().key(key).metadata(metadata(metadata)).build();
	}

	private static Document metadata(Map<String, Object> metadata) {
		Map<String, Document> values = new LinkedHashMap<>(metadata.size());
		metadata.forEach((key, value) -> values.put(key, DocumentUtils.toDocument(value)));
		return Document.fromMap(values);
	}

}
