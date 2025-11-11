/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(request.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(request.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
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

	@Test
	void shouldBuildRequestWithOnlyQuery() {
		String query = "test query";
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().query(query).build();

		assertThat(request.getQuery()).isEqualTo(query);
		assertThat(request.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(request.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(request.getNativeExpression()).isNull();
	}

	@Test
	void shouldBuildRequestWithOnlyTopK() {
		int topK = 1;
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().topK(topK).build();

		assertThat(request.getQuery()).isEmpty();
		assertThat(request.getTopK()).isEqualTo(topK);
		assertThat(request.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
	}

	@Test
	void shouldBuildRequestWithOnlySimilarityThreshold() {
		double threshold = 0.95;
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().similarityThreshold(threshold).build();

		assertThat(request.getQuery()).isEmpty();
		assertThat(request.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
		assertThat(request.getSimilarityThreshold()).isEqualTo(threshold);
	}

	@Test
	void shouldHandleEmptyQuery() {
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().query("").topK(1).build();

		assertThat(request.getQuery()).isEmpty();
		assertThat(request.getTopK()).isEqualTo(1);
	}

	@Test
	void shouldHandleComplexNativeExpression() {
		String complexExpression = "(level > 1 AND type = 'type1') OR (mode IN ['mode1'] AND value >= 0.1)";
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().nativeExpression(complexExpression).build();

		assertThat(request.getNativeExpression()).isEqualTo(complexExpression);
	}

	@Test
	void shouldHandleComplexSearchParamsJson() {
		String complexJson = "{\"values\":{\"value1\":1,\"value2\":1.1},\"value\":{\"value\":1,\"value\":1}}";
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder().searchParamsJson(complexJson).build();

		assertThat(request.getSearchParamsJson()).isEqualTo(complexJson);
	}

	@Test
	void shouldUpdateFieldsWithMultipleCalls() {
		MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
			.query("initial")
			.query("updated")
			.topK(1)
			.topK(1)
			.build();

		assertThat(request.getQuery()).isEqualTo("updated");
		assertThat(request.getTopK()).isEqualTo(1);
	}

}
