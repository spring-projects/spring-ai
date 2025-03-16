package org.springframework.ai.vectorstore.milvus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.SearchRequest.DEFAULT_TOP_K;
import static org.springframework.ai.vectorstore.SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;

/**
 * Test class for verifying the functionality of the {@link MilvusSearchRequest} class.
 *
 * @author waileong
 */
class MilvusSearchRequestTest {

	@Test
	void shouldBuildMilvusSearchRequestWithNativeExpression() {
		String query = "sample query";
		int topK = 10;
		double similarityThreshold = 0.8;
		String nativeExpression = "city LIKE 'New%'";
		String searchParamsJson = "{\"nprobe\":128}";

		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
			.query(query)
			.topK(topK)
			.similarityThreshold(similarityThreshold)
			.nativeExpression(nativeExpression)
			.searchParamsJson(searchParamsJson)
			.build();

		assertThat(request.getQuery()).isEqualTo(query);
		assertThat(request.getTopK()).isEqualTo(topK);
		assertThat(request.getSimilarityThreshold()).isEqualTo(similarityThreshold);
		assertThat(request.getNativeExpression()).isEqualTo(nativeExpression);
		assertThat(request.getSearchParamsJson()).isEqualTo(searchParamsJson);
	}

	@Test
	void shouldBuildMilvusSearchRequestWithDefaults() {
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().build();

		assertThat(request.getQuery()).isEmpty();
		assertThat(request.getTopK()).isEqualTo(DEFAULT_TOP_K);
		assertThat(request.getSimilarityThreshold()).isEqualTo(SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(request.getNativeExpression()).isNull();
		assertThat(request.getSearchParamsJson()).isNull();
	}

	@Test
	void shouldAllowSettingNativeExpressionIndependently() {
		String nativeExpression = "age > 30";
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().nativeExpression(nativeExpression).build();

		assertThat(request.getNativeExpression()).isEqualTo(nativeExpression);
	}

	@Test
	void shouldAllowSettingSearchParamsJsonIndependently() {
		String searchParamsJson = "{\"metric_type\": \"IP\"}";
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().searchParamsJson(searchParamsJson).build();

		assertThat(request.getSearchParamsJson()).isEqualTo(searchParamsJson);
	}

}
