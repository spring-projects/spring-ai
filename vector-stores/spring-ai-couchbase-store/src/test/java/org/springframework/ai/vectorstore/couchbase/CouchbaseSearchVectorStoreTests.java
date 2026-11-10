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

package org.springframework.ai.vectorstore.couchbase;

import java.util.List;
import java.util.Map;

import com.couchbase.client.core.error.AuthenticationFailureException;
import com.couchbase.client.core.error.IndexNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.manager.search.ScopeSearchIndexManager;
import com.couchbase.client.java.manager.search.SearchIndex;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link CouchbaseSearchVectorStore} resolves the vector size from the
 * search index and checks caller-supplied vectors.
 *
 * @author Soby Chacko
 */
class CouchbaseSearchVectorStoreTests {

	private static final String INDEX_NAME = "test-index";

	private final Cluster cluster = mock(Cluster.class);

	private final Scope scope = mock(Scope.class);

	private final Collection collection = mock(Collection.class);

	private final ScopeSearchIndexManager searchIndexes = mock(ScopeSearchIndexManager.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	CouchbaseSearchVectorStoreTests() {
		Bucket bucket = mock(Bucket.class);
		when(this.cluster.bucket(anyString())).thenReturn(bucket);
		when(bucket.scope(anyString())).thenReturn(this.scope);
		when(this.scope.collection(anyString())).thenReturn(this.collection);
		when(this.scope.searchIndexes()).thenReturn(this.searchIndexes);
		when(this.collection.name()).thenReturn("springCollection");
	}

	@Test
	void buildingTheStoreDoesNotAskTheModel() {
		store();

		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void sizeIsReadFromTheIndex() {
		when(this.searchIndexes.getIndex(INDEX_NAME)).thenReturn(index(4));

		assertThat(store().createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheIndex() {
		CouchbaseSearchVectorStore store = CouchbaseSearchVectorStore.builder(this.cluster, this.embeddingModel)
			.scopeName("springScope")
			.collectionName("springCollection")
			.vectorIndexName(INDEX_NAME)
			.dimensions(3)
			.build();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(3);
		verify(this.searchIndexes, never()).getIndex(anyString());
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aMissingIndexLeavesTheSizeUnknownAndUpsertIsRefused() {
		when(this.searchIndexes.getIndex(INDEX_NAME)).thenThrow(new IndexNotFoundException(INDEX_NAME));
		CouchbaseSearchVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry("a", 4))))
			.withMessageContaining(INDEX_NAME);
		verify(this.collection, never()).upsert(anyString(), any());
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void anIndexWithoutAVectorFieldForTheCollectionLeavesTheSizeUnknown() {
		when(this.searchIndexes.getIndex(INDEX_NAME)).thenReturn(index("otherScope.otherCollection", 4));
		CouchbaseSearchVectorStore store = store();

		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry("a", 4))))
			.withMessageContaining(INDEX_NAME);
		verify(this.collection, never()).upsert(anyString(), any());
	}

	@Test
	void aVectorFieldIndexedUnderAnotherNameIsNotUsed() {
		// The query searches the field "embedding", so a vector indexed as "otherVector"
		// would never match
		when(this.searchIndexes.getIndex(INDEX_NAME))
			.thenReturn(index("springScope.springCollection", "otherVector", 4));
		CouchbaseSearchVectorStore store = store();

		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry("a", 4))))
			.withMessageContaining(INDEX_NAME);
		verify(this.collection, never()).upsert(anyString(), any());
	}

	@Test
	void aFailedReadIsNotCachedAndTheIndexIsReadAgain() {
		when(this.searchIndexes.getIndex(INDEX_NAME))
			.thenThrow(new AuthenticationFailureException("denied", null, null))
			.thenReturn(index(4));
		CouchbaseSearchVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.searchIndexes, times(2)).getIndex(INDEX_NAME);
	}

	@Test
	void upsertChecksTheWholeBatchBeforeWriting() {
		when(this.searchIndexes.getIndex(INDEX_NAME)).thenReturn(index(4));
		CouchbaseSearchVectorStore store = store();

		assertThatIllegalArgumentException().isThrownBy(() -> store.upsert(List.of(entry("a", 4), entry("b", 3))))
			.withMessageContaining("Embedding at index 1");
		verify(this.collection, never()).upsert(anyString(), any());

		store.upsert(List.of(entry("a", 4), entry("b", 4)));
		verify(this.collection).upsert(eq("a"), any(CouchbaseSearchVectorStore.CouchbaseDocument.class));
		verify(this.collection).upsert(eq("b"), any(CouchbaseSearchVectorStore.CouchbaseDocument.class));
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aQueryEmbeddingOfTheWrongSizeIsRejectedBeforeQuerying() {
		when(this.searchIndexes.getIndex(INDEX_NAME)).thenReturn(index(4));
		CouchbaseSearchVectorStore store = store();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.similaritySearch(new float[3], SearchRequest.builder().build()))
			.withMessageContaining("Query embedding");
		verify(this.scope, never()).query(anyString(), any());
		verifyNoInteractions(this.embeddingModel);
	}

	private CouchbaseSearchVectorStore store() {
		return CouchbaseSearchVectorStore.builder(this.cluster, this.embeddingModel)
			.scopeName("springScope")
			.collectionName("springCollection")
			.vectorIndexName(INDEX_NAME)
			.build();
	}

	private static SearchIndex index(int dims) {
		return index("springScope.springCollection", dims);
	}

	private static SearchIndex index(String type, int dims) {
		return index(type, "embedding", dims);
	}

	private static SearchIndex index(String type, String fieldName, int dims) {
		return SearchIndex.fromJson("""
				{"name": "%s", "type": "fulltext-index", "sourceName": "springBucket",
				"params": {"mapping": {"types": {"%s": {"properties": {
				"embedding": {"fields": [{"name": "%s", "type": "vector", "dims": %d}]}}}}}}}
				""".formatted(INDEX_NAME, type, fieldName, dims));
	}

	private static EmbeddedDocument entry(String id, int dimensions) {
		return new EmbeddedDocument(new Document(id, "text", Map.of()), new float[dimensions]);
	}

}
