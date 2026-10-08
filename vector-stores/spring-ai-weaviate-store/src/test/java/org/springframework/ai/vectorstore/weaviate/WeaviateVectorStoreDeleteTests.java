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

package org.springframework.ai.vectorstore.weaviate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.batch.api.ObjectsBatchDeleter;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.Raw;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.MetadataField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeaviateVectorStore#delete(Filter.Expression)}.
 *
 * @author lejuho
 */
class WeaviateVectorStoreDeleteTests {

	/** Default QUERY_MAXIMUM_RESULTS of a Weaviate server. */
	private static final int QUERY_MAXIMUM_RESULTS = 10000;

	private final Filter.Expression filter = new FilterExpressionBuilder().eq("country", "BG").build();

	@Test
	void deleteByFilterRemovesMatchesBeyondOneQueryPage() {
		FakeWeaviate weaviate = new FakeWeaviate(QUERY_MAXIMUM_RESULTS + 1);

		weaviate.vectorStore().delete(this.filter);

		assertThat(weaviate.storedIds).isEmpty();
		assertThat(weaviate.queries).hasValue(2);
		assertThat(weaviate.deletions).hasValue(2);
	}

	@Test
	void deleteByFilterWithExactlyOneFullPage() {
		FakeWeaviate weaviate = new FakeWeaviate(QUERY_MAXIMUM_RESULTS);

		weaviate.vectorStore().delete(this.filter);

		assertThat(weaviate.storedIds).isEmpty();
		assertThat(weaviate.queries).hasValue(2);
		assertThat(weaviate.deletions).hasValue(1);
	}

	@Test
	void deleteByFilterWithFewMatches() {
		FakeWeaviate weaviate = new FakeWeaviate(3);

		weaviate.vectorStore().delete(this.filter);

		assertThat(weaviate.storedIds).isEmpty();
		assertThat(weaviate.queries).hasValue(1);
		assertThat(weaviate.deletions).hasValue(1);
	}

	@Test
	void deleteByFilterWithoutMatches() {
		FakeWeaviate weaviate = new FakeWeaviate(0);

		weaviate.vectorStore().delete(this.filter);

		assertThat(weaviate.queries).hasValue(1);
		assertThat(weaviate.deletions).hasValue(0);
	}

	/**
	 * Mocked {@link WeaviateClient} backed by a set of matching object ids. Like a
	 * Weaviate server, a query returns at most the requested limit, capped at
	 * {@link #QUERY_MAXIMUM_RESULTS}.
	 */
	private static final class FakeWeaviate {

		final TreeSet<String> storedIds = new TreeSet<>();

		final AtomicInteger queries = new AtomicInteger();

		final AtomicInteger deletions = new AtomicInteger();

		private final WeaviateClient client = mock(WeaviateClient.class, RETURNS_DEEP_STUBS);

		FakeWeaviate(int matchingDocuments) {
			for (int i = 0; i < matchingDocuments; i++) {
				this.storedIds.add(String.format("%08d-0000-0000-0000-000000000000", i));
			}
			mockQueries();
			mockDeletions();
		}

		WeaviateVectorStore vectorStore() {
			EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
			when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1.0f, 0.0f });
			return WeaviateVectorStore.builder(this.client, embeddingModel)
				.filterMetadataFields(List.of(MetadataField.text("country")))
				.build();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private void mockQueries() {
			Raw raw = mock(Raw.class, RETURNS_SELF);
			when(this.client.graphQL().raw()).thenReturn(raw);
			when(raw.run()).thenAnswer(invocation -> {
				this.queries.incrementAndGet();
				List<Map<String, ?>> items = new ArrayList<>();
				for (String id : this.storedIds) {
					if (items.size() == QUERY_MAXIMUM_RESULTS) {
						break;
					}
					items.add(Map.of("_additional", Map.of("id", id, "certainty", 1.0d), "content", "text"));
				}
				GraphQLResponse response = mock(GraphQLResponse.class);
				when(response.getData()).thenReturn(Map.of("Get", Map.of("SpringAiWeaviate", items)));
				Result result = mock(Result.class);
				when(result.getResult()).thenReturn(response);
				return result;
			});
		}

		@SuppressWarnings("unchecked")
		private void mockDeletions() {
			ObjectsBatchDeleter deleter = mock(ObjectsBatchDeleter.class, RETURNS_SELF);
			when(this.client.batch().objectsBatchDeleter()).thenReturn(deleter);
			AtomicReference<WhereFilter> where = new AtomicReference<>();
			doAnswer(invocation -> {
				where.set(invocation.getArgument(0));
				return deleter;
			}).when(deleter).withWhere(any());
			when(deleter.run()).thenAnswer(invocation -> {
				this.deletions.incrementAndGet();
				WhereFilter filter = where.get();
				String[] ids = (filter.getValueStringArray() != null) ? filter.getValueStringArray()
						: new String[] { filter.getValueString() };
				Arrays.asList(ids).forEach(this.storedIds::remove);
				return mock(Result.class);
			});
		}

	}

}
