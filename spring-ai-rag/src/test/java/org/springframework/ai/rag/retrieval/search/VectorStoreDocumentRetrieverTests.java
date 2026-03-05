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

package org.springframework.ai.rag.retrieval.search;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Unit tests for {@link VectorStoreDocumentRetriever}.
 *
 * @author Thomas Vitale
 */
class VectorStoreDocumentRetrieverTests {

	@Test
	void whenVectorStoreIsNullThenThrow() {
		assertThatThrownBy(() -> VectorStoreDocumentRetriever.builder().vectorStore(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("vectorStore cannot be null");
	}

	@Test
	void whenTopKIsZeroThenThrow() {
		assertThatThrownBy(
				() -> VectorStoreDocumentRetriever.builder().topK(0).vectorStore(mock(VectorStore.class)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topK must be greater than 0");
	}

	@Test
	void whenTopKIsNegativeThenThrow() {
		assertThatThrownBy(
				() -> VectorStoreDocumentRetriever.builder().topK(-1).vectorStore(mock(VectorStore.class)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topK must be greater than 0");
	}

	@Test
	void whenSimilarityThresholdIsNegativeThenThrow() {
		assertThatThrownBy(() -> VectorStoreDocumentRetriever.builder()
			.similarityThreshold(-1.0)
			.vectorStore(mock(VectorStore.class))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("similarityThreshold must be equal to or greater than 0.0");
	}

	@Test
	void searchRequestParameters() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder()
			.vectorStore(mockVectorStore)
			.similarityThreshold(0.73)
			.topK(5)
			.filterExpression(new Filter.Expression(EQ, new Filter.Key("location"), new Filter.Value("Rivendell")))
			.build();

		documentRetriever.retrieve(new Query("query"));

		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getQuery()).isEqualTo("query");
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(0.73);
		assertThat(searchRequest.getTopK()).isEqualTo(5);
		assertThat(searchRequest.getFilterExpression())
			.isEqualTo(new Filter.Expression(EQ, new Filter.Key("location"), new Filter.Value("Rivendell")));
	}

	@Test
	void dynamicFilterExpressions() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder()
			.vectorStore(mockVectorStore)
			.filterExpression(
					() -> new FilterExpressionBuilder().eq("tenantId", TenantContextHolder.getTenantIdentifier())
						.build())
			.build();

		TenantContextHolder.setTenantIdentifier("tenant1");
		documentRetriever.retrieve(new Query("query"));
		TenantContextHolder.clear();

		TenantContextHolder.setTenantIdentifier("tenant2");
		documentRetriever.retrieve(new Query("query"));
		TenantContextHolder.clear();

		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

		verify(mockVectorStore, new Times(2)).similaritySearch(searchRequestCaptor.capture());

		var searchRequest1 = searchRequestCaptor.getAllValues().get(0);
		assertThat(searchRequest1.getFilterExpression())
			.isEqualTo(new Filter.Expression(EQ, new Filter.Key("tenantId"), new Filter.Value("tenant1")));

		var searchRequest2 = searchRequestCaptor.getAllValues().get(1);
		assertThat(searchRequest2.getFilterExpression())
			.isEqualTo(new Filter.Expression(EQ, new Filter.Key("tenantId"), new Filter.Value("tenant2")));
	}

	@Test
	void whenQueryObjectIsNullThenThrow() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(mockVectorStore).build();

		Query nullQuery = null;
		assertThatThrownBy(() -> documentRetriever.retrieve(nullQuery)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void defaultValuesAreAppliedWhenNotSpecified() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(mockVectorStore).build();

		documentRetriever.retrieve(new Query("test query"));

		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(searchRequest.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(searchRequest.getFilterExpression()).isNull();
	}

	@Test
	void retrieveWithQueryObject() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder()
			.vectorStore(mockVectorStore)
			.similarityThreshold(0.85)
			.topK(3)
			.filterExpression(new Filter.Expression(EQ, new Filter.Key("category"), new Filter.Value("books")))
			.build();

		var query = new Query("test query");
		documentRetriever.retrieve(query);

		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getQuery()).isEqualTo("test query");
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(0.85);
		assertThat(searchRequest.getTopK()).isEqualTo(3);
		assertThat(searchRequest.getFilterExpression())
			.isEqualTo(new Filter.Expression(EQ, new Filter.Key("category"), new Filter.Value("books")));
	}

	@Test
	void retrieveWithQueryObjectAndDefaultValues() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(mockVectorStore).build();

		// Setup mock to return some documents
		List<Document> mockDocuments = List.of(new Document("content1", Map.of("id", "1")),
				new Document("content2", Map.of("id", "2")));
		when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments);

		var query = new Query("test query");
		var result = documentRetriever.retrieve(query);

		// Verify the mock interaction
		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		// Verify the search request
		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getQuery()).isEqualTo("test query");
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(searchRequest.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(searchRequest.getFilterExpression()).isNull();

		// Verify the returned documents
		assertThat(result).hasSize(2).containsExactlyElementsOf(mockDocuments);
	}

	@Test
	void retrieveWithQueryObjectAndRequestFilterExpression() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(mockVectorStore).build();

		var query = Query.builder()
			.text("test query")
			.context(Map.of(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "location == 'Rivendell'"))
			.build();
		documentRetriever.retrieve(query);

		// Verify the mock interaction
		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		// Verify the search request
		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getQuery()).isEqualTo("test query");
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(searchRequest.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(searchRequest.getFilterExpression())
			.isEqualTo(new FilterExpressionBuilder().eq("location", "Rivendell").build());
	}

	@Test
	void retrieveWithQueryObjectAndFilterExpressionObject() {
		var mockVectorStore = mock(VectorStore.class);
		var documentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(mockVectorStore).build();

		// Create a Filter.Expression object directly
		var filterExpression = new Filter.Expression(EQ, new Filter.Key("location"), new Filter.Value("Rivendell"));

		var query = Query.builder()
			.text("test query")
			.context(Map.of(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression))
			.build();
		documentRetriever.retrieve(query);

		// Verify the mock interaction
		var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(mockVectorStore).similaritySearch(searchRequestCaptor.capture());

		// Verify the search request
		var searchRequest = searchRequestCaptor.getValue();
		assertThat(searchRequest.getQuery()).isEqualTo("test query");
		assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(searchRequest.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(searchRequest.getFilterExpression()).isEqualTo(filterExpression);
	}

	static final class TenantContextHolder {

		private static final ThreadLocal<String> tenantIdentifier = new ThreadLocal<>();

		private TenantContextHolder() {
		}

		public static void setTenantIdentifier(String tenant) {
			Assert.hasText(tenant, "tenant cannot be null or empty");
			tenantIdentifier.set(tenant);
		}

		public static String getTenantIdentifier() {
			return tenantIdentifier.get();
		}

		public static void clear() {
			tenantIdentifier.remove();
		}

	}

}
