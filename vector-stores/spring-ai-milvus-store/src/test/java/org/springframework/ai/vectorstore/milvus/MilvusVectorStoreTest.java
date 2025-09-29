/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.milvus;

import java.util.List;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test class for {@link MilvusVectorStore}.
 *
 * @author waileong
 */
@ExtendWith(MockitoExtension.class)
class MilvusVectorStoreTest {

	@Mock
	private MilvusServiceClient milvusClient;

	@Mock
	private EmbeddingModel embeddingModel;

	private MilvusVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel).build();
	}

	@Test
	void shouldPerformSimilaritySearchWithNativeExpression() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.nativeExpression("metadata[\"age\"] > 30") // this has higher priority
				.filterExpression("age <= 30") // this will be ignored
				.searchParamsJson("{\"nprobe\":128}")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);
			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo(request.getNativeExpression());
			assertThat(capturedParam.getParams()).isEqualTo(request.getSearchParamsJson());
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithFilterExpression() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30")
				.searchParamsJson("{\"nprobe\":128}")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo("metadata[\"age\"] > 30"); // filter
			assertThat(capturedParam.getParams()).isEqualTo(request.getSearchParamsJson());
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithOriginalSearchRequest() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo("metadata[\"age\"] > 30"); // filter
			assertThat(capturedParam.getParams()).isEqualTo("{}");
		}
	}

	private SearchParam performSimilaritySearch(MockedStatic<EmbeddingUtils> mockedEmbeddingUtils,
			SearchRequest request) {
		List<Float> mockVector = List.of(1.0f, 2.0f, 3.0f);
		mockedEmbeddingUtils.when(() -> EmbeddingUtils.toList(any())).thenReturn(mockVector);

		SearchResults mockResults = mock(SearchResults.class);
		when(mockResults.getResults()).thenReturn(SearchResultData.getDefaultInstance());

		R<SearchResults> mockResponse = R.success(mockResults);
		when(this.milvusClient.search(any(SearchParam.class))).thenReturn(mockResponse);

		ArgumentCaptor<SearchParam> searchParamCaptor = ArgumentCaptor.forClass(SearchParam.class);

		List<Document> results = this.vectorStore.doSimilaritySearch(request);

		assertThat(results).isNotNull();
		verify(this.milvusClient).search(searchParamCaptor.capture());
		return searchParamCaptor.getValue();
	}

}
