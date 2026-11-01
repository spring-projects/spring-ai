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

package org.springframework.ai.vectorstore.qdrant;

import java.util.List;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionConfig;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.CollectionParams;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link QdrantVectorStore} learns the vector size of a collection.
 *
 * @author Soby Chacko
 */
class QdrantVectorStoreDimensionsTests {

	private static CollectionInfo collectionOfSize(long size) {
		VectorParams params = VectorParams.newBuilder().setSize(size).setDistance(Distance.Cosine).build();
		return CollectionInfo.newBuilder()
			.setConfig(
					CollectionConfig.newBuilder()
						.setParams(CollectionParams.newBuilder()
							.setVectorsConfig(VectorsConfig.newBuilder().setParams(params))))
			.build();
	}

	private static Integer observedDimensions(QdrantVectorStore store) {
		return store.createObservationContextBuilder("test").build().getDimensions();
	}

	@Test
	void failedCollectionReadLeavesTheSizeUnknownAndIsRetried() {
		QdrantClient client = mock(QdrantClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(client.getCollectionInfoAsync(anyString()))
			.thenReturn(Futures.immediateFailedFuture(new IllegalStateException("unavailable")))
			.thenReturn(Futures.immediateFuture(collectionOfSize(4)));
		QdrantVectorStore store = QdrantVectorStore.builder(client, embeddingModel).build();

		// The read fails and the model must not stand in for the collection
		assertThat(observedDimensions(store)).isNull();
		// Once the collection can be read again the real size applies
		assertThat(observedDimensions(store)).isEqualTo(4);
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void knownSizeIsReadOnce() {
		QdrantClient client = mock(QdrantClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(client.getCollectionInfoAsync(anyString())).thenReturn(Futures.immediateFuture(collectionOfSize(4)));
		QdrantVectorStore store = QdrantVectorStore.builder(client, embeddingModel).build();

		assertThat(observedDimensions(store)).isEqualTo(4);
		assertThat(observedDimensions(store)).isEqualTo(4);
		verify(client, times(1)).getCollectionInfoAsync(anyString());
	}

	@Test
	void configuredSizeIsUsedWithoutReadingTheCollection() {
		QdrantClient client = mock(QdrantClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		QdrantVectorStore store = QdrantVectorStore.builder(client, embeddingModel).dimensions(7).build();

		assertThat(observedDimensions(store)).isEqualTo(7);
		verify(client, never()).getCollectionInfoAsync(any());
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void createdCollectionKeepsTheSizeItWasCreatedWith() throws Exception {
		QdrantClient client = mock(QdrantClient.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(client.listCollectionsAsync()).thenReturn(Futures.immediateFuture(List.of()));
		when(client.createCollectionAsync(anyString(), any(VectorParams.class))).thenReturn(Futures
			.immediateFuture(io.qdrant.client.grpc.Collections.CollectionOperationResponse.getDefaultInstance()));
		QdrantVectorStore store = QdrantVectorStore.builder(client, embeddingModel).initializeSchema(true).build();

		store.afterPropertiesSet();

		assertThat(observedDimensions(store)).isEqualTo(3);
		verify(client, never()).getCollectionInfoAsync(any());
	}

}
