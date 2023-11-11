/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore.filter;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser.FilterExpressionParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class SearchRequestTests {

	@Test
	public void createDefaults() {
		var emptyRequest = SearchRequest.defaults();
		assertThat(emptyRequest.getQuery()).isEqualTo("");
		checkDefaults(emptyRequest);
	}

	@Test
	public void createQuery() {
		var emptyRequest = SearchRequest.query("New Query");
		assertThat(emptyRequest.getQuery()).isEqualTo("New Query");
		checkDefaults(emptyRequest);
	}

	@Test
	public void createFrom() {
		var originalRequest = SearchRequest.query("New Query")
			.withTopK(696)
			.withSimilarityThreshold(0.678)
			.withFilterExpression("country == 'NL'");

		var newRequest = SearchRequest.from(originalRequest);

		assertThat(newRequest).isNotSameAs(originalRequest);
		assertThat(newRequest.getQuery()).isEqualTo(originalRequest.getQuery());
		assertThat(newRequest.getTopK()).isEqualTo(originalRequest.getTopK());
		assertThat(newRequest.getFilterExpression()).isEqualTo(originalRequest.getFilterExpression());
		assertThat(newRequest.getSimilarityThreshold()).isEqualTo(originalRequest.getSimilarityThreshold());
	}

	@Test
	public void withQuery() {
		var emptyRequest = SearchRequest.defaults();
		assertThat(emptyRequest.getQuery()).isEqualTo("");

		emptyRequest.withQuery("New Query");
		assertThat(emptyRequest.getQuery()).isEqualTo("New Query");
	}

	@Test()
	public void withSimilarityThreshold() {
		var request = SearchRequest.query("Test").withSimilarityThreshold(0.678);
		assertThat(request.getSimilarityThreshold()).isEqualTo(0.678);

		request.withSimilarityThreshold(0.9);
		assertThat(request.getSimilarityThreshold()).isEqualTo(0.9);

		assertThatThrownBy(() -> {
			request.withSimilarityThreshold(-1);
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Similarity threshold must be in [0,1] range.");

		assertThatThrownBy(() -> {
			request.withSimilarityThreshold(1.1);
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Similarity threshold must be in [0,1] range.");

	}

	@Test()
	public void withTopK() {
		var request = SearchRequest.query("Test").withTopK(66);
		assertThat(request.getTopK()).isEqualTo(66);

		request.withTopK(89);
		assertThat(request.getTopK()).isEqualTo(89);

		assertThatThrownBy(() -> {
			request.withTopK(-1);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TopK should be positive.");

	}

	@Test()
	public void withFilterExpression() {

		var request = SearchRequest.query("Test").withFilterExpression("country == 'BG' && year >= 2022");
		assertThat(request.getFilterExpression()).isEqualTo(new Filter.Expression(Filter.ExpressionType.AND,
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("BG")),
				new Filter.Expression(Filter.ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2022))));
		assertThat(request.hasFilterExpression()).isTrue();

		request.withFilterExpression("active == true");
		assertThat(request.getFilterExpression()).isEqualTo(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("active"), new Filter.Value(true)));
		assertThat(request.hasFilterExpression()).isTrue();

		request.withFilterExpression(Filter.builder().eq("country", "NL").build());
		assertThat(request.getFilterExpression()).isEqualTo(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("NL")));
		assertThat(request.hasFilterExpression()).isTrue();

		request.withFilterExpression((String) null);
		assertThat(request.getFilterExpression()).isNull();
		assertThat(request.hasFilterExpression()).isFalse();

		request.withFilterExpression((Filter.Expression) null);
		assertThat(request.getFilterExpression()).isNull();
		assertThat(request.hasFilterExpression()).isFalse();

		assertThatThrownBy(() -> {
			request.withFilterExpression("FooBar");
		}).isInstanceOf(FilterExpressionParseException.class)
			.hasMessageContaining("Error: no viable alternative at input 'FooBar'");

	}

	private void checkDefaults(SearchRequest request) {
		assertThat(request.getFilterExpression()).isNull();
		assertThat(request.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(request.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
	}

}
