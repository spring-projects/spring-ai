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

package org.springframework.ai.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.Assert;

import java.util.Objects;

/**
 * Similarity search request builder. Use the {@link #query(String)}, {@link #defaults()}
 * or {@link #from(SearchRequest)} factory methods to create a new {@link SearchRequest}
 * instance and then apply the 'with' methods to alter the default values.
 *
 * @author Christian Tzolov
 */
public class SearchRequest {

	/**
	 * Similarity threshold that accepts all search scores. A threshold value of 0.0 means
	 * any similarity is accepted or disable the similarity threshold filtering. A
	 * threshold value of 1.0 means an exact match is required.
	 */
	public static final double SIMILARITY_THRESHOLD_ACCEPT_ALL = 0.0;

	/**
	 * Default value for the top 'k' similar results to return.
	 */
	public static final int DEFAULT_TOP_K = 4;

	public String query;

	private int topK = DEFAULT_TOP_K;

	private double similarityThreshold = SIMILARITY_THRESHOLD_ACCEPT_ALL;

	private Filter.Expression filterExpression;

	private SearchRequest(String query) {
		this.query = query;
	}

	/**
	 * Create a new {@link SearchRequest} builder instance with specified embedding query
	 * string.
	 * @param query Text to use for embedding similarity comparison.
	 * @return Returns new {@link SearchRequest} builder instance.
	 */
	public static SearchRequest query(String query) {
		Assert.notNull(query, "Query can not be null.");
		return new SearchRequest(query);
	}

	/**
	 * Create a new {@link SearchRequest} builder instance with an empty embedding query
	 * string. Use the {@link #withQuery(String query)} to set/update the embedding query
	 * text.
	 * @return Returns new {@link SearchRequest} builder instance.
	 */
	public static SearchRequest defaults() {
		return new SearchRequest("");
	}

	/**
	 * Copy an existing {@link SearchRequest} instance.
	 * @param originalSearchRequest {@link SearchRequest} instance to copy.
	 * @return Returns new {@link SearchRequest} builder instance.
	 */
	public static SearchRequest from(SearchRequest originalSearchRequest) {
		return new SearchRequest(originalSearchRequest.getQuery()).withTopK(originalSearchRequest.getTopK())
			.withSimilarityThreshold(originalSearchRequest.getSimilarityThreshold())
			.withFilterExpression(originalSearchRequest.getFilterExpression());
	}

	/**
	 * @param query Text to use for embedding similarity comparison.
	 * @return this builder.
	 */
	public SearchRequest withQuery(String query) {
		Assert.notNull(query, "Query can not be null.");
		this.query = query;
		return this;
	}

	/**
	 * @param topK the top 'k' similar results to return.
	 * @return this builder.
	 */
	public SearchRequest withTopK(int topK) {
		Assert.isTrue(topK >= 0, "TopK should be positive.");
		this.topK = topK;
		return this;
	}

	/**
	 * Similarity threshold score to filter the search response by. Only documents with
	 * similarity score equal or greater than the 'threshold' will be returned. Note that
	 * this is a post-processing step performed on the client not the server side. A
	 * threshold value of 0.0 means any similarity is accepted or disable the similarity
	 * threshold filtering. A threshold value of 1.0 means an exact match is required.
	 * @param threshold The lower bound of the similarity score.
	 * @return this builder.
	 */
	public SearchRequest withSimilarityThreshold(double threshold) {
		Assert.isTrue(threshold >= 0 && threshold <= 1, "Similarity threshold must be in [0,1] range.");
		this.similarityThreshold = threshold;
		return this;
	}

	/**
	 * Sets disables the similarity threshold by setting it to 0.0 - all results are
	 * accepted.
	 * @return this builder.
	 */
	public SearchRequest withSimilarityThresholdAll() {
		return withSimilarityThreshold(SIMILARITY_THRESHOLD_ACCEPT_ALL);
	}

	/**
	 * Retrieves documents by query embedding similarity and matching the filters. Value
	 * of 'null' means that no metadata filters will be applied to the search.
	 *
	 * For example if the {@link Document#getMetadata()} schema is:
	 *
	 * <pre>{@code
	 * &#123;
	 * "country": <Text>,
	 * "city": <Text>,
	 * "year": <Number>,
	 * "price": <Decimal>,
	 * "isActive": <Boolean>
	 * &#125;
	 * }</pre>
	 *
	 * you can constrain the search result to only UK countries with isActive=true and
	 * year equal or greater 2020. You can build this such metadata filter
	 * programmatically like this:
	 *
	 * <pre>{@code
	 * var exp = new Filter.Expression(AND,
	 * 		new Expression(EQ, new Key("country"), new Value("UK")),
	 * 		new Expression(AND,
	 * 				new Expression(GTE, new Key("year"), new Value(2020)),
	 * 				new Expression(EQ, new Key("isActive"), new Value(true))));
	 * }</pre>
	 *
	 * The {@link Filter.Expression} is portable across all vector stores.<br/>
	 *
	 *
	 * The {@link FilterExpressionBuilder} is a DSL creating expressions programmatically:
	 *
	 * <pre>{@code
	 * var b = new FilterExpressionBuilder();
	 * var exp = b.and(
	 * 		b.eq("country", "UK"),
	 * 		b.and(
	 * 			b.gte("year", 2020),
	 * 			b.eq("isActive", true)));
	 * }</pre>
	 *
	 * The {@link FilterExpressionTextParser} converts textual, SQL like filter expression
	 * language into {@link Filter.Expression}:
	 *
	 * <pre>{@code
	 * var parser = new FilterExpressionTextParser();
	 * var exp = parser.parse("country == 'UK' && isActive == true && year >=2020");
	 * }</pre>
	 * @param expression {@link Filter.Expression} instance used to define the metadata
	 * filter criteria. The 'null' value stands for no expression filters.
	 * @return this builder.
	 */
	public SearchRequest withFilterExpression(Filter.Expression expression) {
		this.filterExpression = expression;
		return this;
	}

	/**
	 * Document metadata filter expression. For example if your
	 * {@link Document#getMetadata()} has a schema like:
	 *
	 * <pre>{@code
	 * &#123;
	 * "country": <Text>,
	 * "city": <Text>,
	 * "year": <Number>,
	 * "price": <Decimal>,
	 * "isActive": <Boolean>
	 * &#125;
	 * }</pre>
	 *
	 * then you can constrain the search result with metadata filter expressions like:
	 *
	 * <pre>{@code
	 * country == 'UK' && year >= 2020 && isActive == true
	 * Or
	 * country == 'BG' && (city NOT IN ['Sofia', 'Plovdiv'] || price < 134.34)
	 * }</pre>
	 *
	 * This ensures that the response contains only embeddings that match the specified
	 * filer criteria. <br/>
	 *
	 * The declarative, SQL like, filter syntax is portable across all vector stores
	 * supporting the filter search feature.<br/>
	 *
	 * The {@link FilterExpressionTextParser} is used to convert the text filter
	 * expression into {@link Filter.Expression}.
	 * @param textExpression declarative, portable, SQL like, metadata filter syntax. The
	 * 'null' value stands for no expression filters.
	 * @return this.builder
	 */
	public SearchRequest withFilterExpression(String textExpression) {
		this.filterExpression = (textExpression != null) ? Filter.parser().parse(textExpression) : null;
		return this;
	}

	public String getQuery() {
		return query;
	}

	public int getTopK() {
		return topK;
	}

	public double getSimilarityThreshold() {
		return similarityThreshold;
	}

	public Filter.Expression getFilterExpression() {
		return filterExpression;
	}

	public boolean hasFilterExpression() {
		return this.filterExpression != null;
	}

	@Override
	public String toString() {
		return "SearchRequest{" + "query='" + query + '\'' + ", topK=" + topK + ", similarityThreshold="
				+ similarityThreshold + ", filterExpression=" + filterExpression + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SearchRequest that = (SearchRequest) o;
		return topK == that.topK && Double.compare(that.similarityThreshold, similarityThreshold) == 0
				&& Objects.equals(query, that.query) && Objects.equals(filterExpression, that.filterExpression);
	}

	@Override
	public int hashCode() {
		return Objects.hash(query, topK, similarityThreshold, filterExpression);
	}

}