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

package org.springframework.ai.vectorstore.couchbase.autoconfigure;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.manager.search.ScopeSearchIndexManager;
import com.couchbase.client.java.manager.search.SearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.couchbase.CouchbaseSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link CouchbaseSearchVectorStoreAutoConfiguration} sets the vector
 * size of the store.
 *
 * @author Soby Chacko
 */
class CouchbaseSearchVectorStoreAutoConfigurationTests {

	private final Cluster cluster = mock(Cluster.class);

	private final ScopeSearchIndexManager searchIndexes = mock(ScopeSearchIndexManager.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CouchbaseSearchVectorStoreAutoConfiguration.class))
		.withBean(Cluster.class, () -> this.cluster)
		.withBean(EmbeddingModel.class, () -> this.embeddingModel);

	@BeforeEach
	void existingIndexOfSize768() {
		Bucket bucket = mock(Bucket.class);
		Scope scope = mock(Scope.class);
		Collection collection = mock(Collection.class);
		when(this.cluster.bucket(anyString())).thenReturn(bucket);
		when(bucket.scope(anyString())).thenReturn(scope);
		when(scope.collection(anyString())).thenReturn(collection);
		when(scope.searchIndexes()).thenReturn(this.searchIndexes);
		when(collection.name()).thenReturn("_default");
		when(this.searchIndexes.getIndex(anyString())).thenReturn(SearchIndex.fromJson("""
				{"name": "spring-ai-document-index", "type": "fulltext-index", "sourceName": "default",
				"params": {"mapping": {"types": {"_default._default": {"properties": {
				"embedding": {"fields": [{"name": "embedding", "type": "vector", "dims": 768}]}}}}}}}
				"""));
	}

	@Test
	void sizeIsReadFromAnExistingIndexWhenNotConfigured() {
		this.contextRunner.run(context -> {
			CouchbaseSearchVectorStore store = context.getBean(CouchbaseSearchVectorStore.class);

			// The index size, not a fixed default or the size the embedding model reports
			assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(768);
			verifyNoInteractions(this.embeddingModel);
		});
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheIndex() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.couchbase.dimensions=384").run(context -> {
			CouchbaseSearchVectorStore store = context.getBean(CouchbaseSearchVectorStore.class);

			assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(384);
			verify(this.searchIndexes, never()).getIndex(anyString());
			verifyNoInteractions(this.embeddingModel);
		});
	}

}
