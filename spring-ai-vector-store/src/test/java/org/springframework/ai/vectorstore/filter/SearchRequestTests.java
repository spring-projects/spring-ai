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

package org.springframework.ai.vectorstore.filter;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser.FilterExpressionParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 */
public class SearchRequestTests {

	@Test
	public void createDefaults() {
		var emptyRequest = SearchRequest.builder().build();
		assertThat(emptyRequest.getQuery()).isEqualTo("");
		checkDefaults(emptyRequest);
	}

	@Test
	public void createQuery() {
		var emptyRequest = SearchRequest.builder().query("New Query").build();
		assertThat(emptyRequest.getQuery()).isEqualTo("New Query");
		checkDefaults(emptyRequest);
	}

	@Test
	public void createFrom() {
		var originalRequest = SearchRequest.builder()
			.query("New Query")
			.topK(696)
			.similarityThreshold(0.678)
			.filterExpression("country == 'NL'")
			.build();

		var newRequest = SearchRequest.from(originalRequest).build();

		assertThat(newRequest).isNotSameAs(originalRequest);
		assertThat(newRequest.getQuery()).isEqualTo(originalRequest.getQuery());
		assertThat(newRequest.getTopK()).isEqualTo(originalRequest.getTopK());
		assertThat(newRequest.getFilterExpression()).isEqualTo(originalRequest.getFilterExpression());
		assertThat(newRequest.getSimilarityThreshold()).isEqualTo(originalRequest.getSimilarityThreshold());
	}

	@Test
	public void queryString() {
		var emptyRequest = SearchRequest.builder().build();
		assertThat(emptyRequest.getQuery()).isEqualTo("");

		var emptyRequest1 = SearchRequest.from(emptyRequest).query("New Query").build();
		assertThat(emptyRequest1.getQuery()).isEqualTo("New Query");
	}

	@Test
	public void similarityThreshold() {
		var request = SearchRequest.builder().query("Test").similarityThreshold(0.678).build();
		assertThat(request.getSimilarityThreshold()).isEqualTo(0.678);

		var request1 = SearchRequest.from(request).similarityThreshold(0.9).build();
		assertThat(request1.getSimilarityThreshold()).isEqualTo(0.9);

		assertThatThrownBy(() -> SearchRequest.from(request).similarityThreshold(-1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Similarity threshold must be in [0,1] range.");

		assertThatThrownBy(() -> SearchRequest.from(request).similarityThreshold(1.1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Similarity threshold must be in [0,1] range.");

	}

	@Test
	public void topK() {
		var request = SearchRequest.builder().query("Test").topK(66).build();
		assertThat(request.getTopK()).isEqualTo(66);

		var request1 = SearchRequest.from(request).topK(89).build();
		assertThat(request1.getTopK()).isEqualTo(89);

		assertThatThrownBy(() -> SearchRequest.from(request).topK(-1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("TopK should be positive.");

	}

	@Test
	public void filterExpression() {

		var request = SearchRequest.builder().query("Test").filterExpression("country == 'BG' && year >= 2022").build();
		assertThat(request.getFilterExpression()).isEqualTo(new Filter.Expression(Filter.ExpressionType.AND,
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("BG")),
				new Filter.Expression(Filter.ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2022))));
		assertThat(request.hasFilterExpression()).isTrue();

		var request1 = SearchRequest.from(request).filterExpression("active == true").build();
		assertThat(request1.getFilterExpression()).isEqualTo(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("active"), new Filter.Value(true)));
		assertThat(request1.hasFilterExpression()).isTrue();

		var request2 = SearchRequest.from(request)
			.filterExpression(new FilterExpressionBuilder().eq("country", "NL").build())
			.build();

		assertThat(request2.getFilterExpression()).isEqualTo(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("NL")));
		assertThat(request2.hasFilterExpression()).isTrue();

		var request3 = SearchRequest.from(request).filterExpression((String) null).build();
		assertThat(request3.getFilterExpression()).isNull();
		assertThat(request3.hasFilterExpression()).isFalse();

		var request4 = SearchRequest.from(request).filterExpression((Filter.Expression) null).build();
		assertThat(request4.getFilterExpression()).isNull();
		assertThat(request4.hasFilterExpression()).isFalse();

		assertThatThrownBy(() -> SearchRequest.from(request).filterExpression("FooBar"))
			.isInstanceOf(FilterExpressionParseException.class)
			.hasMessageContaining("Error: no viable alternative at input 'FooBar'");

	}

	@Test
	public void filterExpressionWithDotAndBackslashIsEscaped() {
		var request = SearchRequest.builder()
			.query("Test")
			.filterExpression("file_name == 'clean.code.pdf' && path == 'C:\\docs\\files'")
			.build();

		var expression = request.getFilterExpression();

		assertThat(expression.toString()).contains("clean\\.code\\.pdf");
		assertThat(expression.toString()).contains("C:\\\\docs\\\\files");
	}

	@Test
	public void filterExpressionWithMultipleLiteralsIsEscapedIndependently() {
		var request = SearchRequest.builder()
			.query("Test")
			.filterExpression("file_name == 'a.b.c' && author == 'me.you'")
			.build();

		var expression = request.getFilterExpression();

		assertThat(expression.toString()).contains("a\\.b\\.c");
		assertThat(expression.toString()).contains("me\\.you");
	}

	@Test
	public void filterExpressionWithVariousFileExtensionsIsEscaped() {
		var request = SearchRequest.builder()
			.query("Test")
			.filterExpression(
					"file_name == 'summary.epub' || file_name == 'lecture_notes.md' || file_name == 'slides.pptx'")
			.build();

		String expression = request.getFilterExpression().toString();

		assertThat(expression).contains("summary\\.epub");
		assertThat(expression).contains("lecture_notes\\.md");
		assertThat(expression).contains("slides\\.pptx");
	}

	@Test
	public void filterExpressionWithInListIsEscaped() {
		var request = SearchRequest.builder()
			.query("Test")
			.filterExpression("file_name IN ['a.pdf', 'b.txt', 'final.report.docx']")
			.build();

		String expression = request.getFilterExpression().toString();

		assertThat(expression).contains("a\\.pdf");
		assertThat(expression).contains("b\\.txt");
		assertThat(expression).contains("final\\.report\\.docx");
	}

	private void checkDefaults(SearchRequest request) {
		assertThat(request.getFilterExpression()).isNull();
		assertThat(request.getSimilarityThreshold()).isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
		assertThat(request.getTopK()).isEqualTo(SearchRequest.DEFAULT_TOP_K);
	}

}
