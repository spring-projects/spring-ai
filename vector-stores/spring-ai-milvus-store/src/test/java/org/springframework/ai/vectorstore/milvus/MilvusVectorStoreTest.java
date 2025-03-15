package org.springframework.ai.vectorstore.milvus;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
		vectorStore = MilvusVectorStore.builder(milvusClient, embeddingModel).build();
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
		when(milvusClient.search(any(SearchParam.class))).thenReturn(mockResponse);

		ArgumentCaptor<SearchParam> searchParamCaptor = ArgumentCaptor.forClass(SearchParam.class);

		List<Document> results = vectorStore.doSimilaritySearch(request);

		assertThat(results).isNotNull();
		verify(milvusClient).search(searchParamCaptor.capture());
		return searchParamCaptor.getValue();
	}

}
