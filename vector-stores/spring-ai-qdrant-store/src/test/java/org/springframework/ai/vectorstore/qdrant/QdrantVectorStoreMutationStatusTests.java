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
import java.util.UUID;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Qdrant mutation status checks on add, upsert and delete-by-id.
 *
 * @author lejuho
 */
class QdrantVectorStoreMutationStatusTests {

	private QdrantClient qdrantClient;

	private EmbeddingModel embeddingModel;

	private QdrantVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		this.qdrantClient = mock(QdrantClient.class);
		this.embeddingModel = mock(EmbeddingModel.class);
		this.vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).build();
	}

	@Test
	void shouldRejectFailedStatusWhenAddingDocuments() {
		when(this.embeddingModel.embed(anyList(), any(), any())).thenReturn(List.of(new float[] { 1.0f, 2.0f, 3.0f }));
		when(this.qdrantClient.upsertAsync(anyString(), anyList())).thenReturn(Futures.immediateFuture(failedUpdate()));

		assertThatThrownBy(() -> this.vectorStore.doAdd(List.of(new Document("doc"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to add documents")
			.hasMessageContaining(UpdateStatus.ClockRejected.name());
	}

	@Test
	void shouldRejectFailedStatusWhenUpsertingDocuments() {
		when(this.qdrantClient.getCollectionInfoAsync(anyString()))
			.thenReturn(Futures.immediateFailedFuture(new IllegalStateException("unavailable")));
		when(this.qdrantClient.upsertAsync(anyString(), anyList())).thenReturn(Futures.immediateFuture(failedUpdate()));

		Document document = new Document(UUID.randomUUID().toString(), "doc", java.util.Map.of());
		assertThatThrownBy(() -> this.vectorStore.doUpsert(List.of(new EmbeddedDocument(document, new float[] { 1f }))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to upsert documents")
			.hasMessageContaining(UpdateStatus.ClockRejected.name());
	}

	@Test
	void shouldRejectFailedStatusWhenDeletingByIdList() {
		when(this.qdrantClient.deleteAsync(anyString(), anyList())).thenReturn(Futures.immediateFuture(failedUpdate()));

		assertThatThrownBy(() -> this.vectorStore.doDelete(List.of(UUID.randomUUID().toString())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to delete documents by id")
			.hasMessageContaining(UpdateStatus.ClockRejected.name());
	}

	@Test
	void shouldRejectFailedStatusWhenDeletingByFilter() {
		when(this.qdrantClient.deleteAsync(anyString(), any(Filter.class)))
			.thenReturn(Futures.immediateFuture(failedUpdate()));

		Expression expression = new Expression(ExpressionType.EQ, new Key("type"), new Value("A"));
		assertThatThrownBy(() -> this.vectorStore.doDelete(expression)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to delete documents by filter")
			.hasMessageContaining(UpdateStatus.ClockRejected.name());
	}

	private static UpdateResult failedUpdate() {
		return UpdateResult.newBuilder().setStatus(UpdateStatus.ClockRejected).build();
	}

}
