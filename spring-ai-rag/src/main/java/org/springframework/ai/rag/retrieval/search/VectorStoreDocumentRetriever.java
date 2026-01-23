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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Retrieves documents from a vector store that are semantically similar to the input
 * query. It supports filtering based on metadata, similarity threshold, and top-k
 * results.
 *
 * <p>
 * Example usage: <pre>{@code
 * VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
 *     .vectorStore(vectorStore)
 *     .similarityThreshold(0.73)
 *     .topK(5)
 *     .filterExpression(filterExpression)
 *     .build();
 * List<Document> documents = retriever.retrieve(new Query("example query"));
 * }</pre>
 *
 * <p>
 * The {@link #FILTER_EXPRESSION} context key can be used to provide a filter expression
 * for a specific query. This key accepts either a string representation of a filter
 * expression or a {@link Filter.Expression} object directly.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class VectorStoreDocumentRetriever implements DocumentRetriever {

	public static final String FILTER_EXPRESSION = "vector_store_filter_expression";

	private final VectorStore vectorStore;

	private final Double similarityThreshold;

	private final Integer topK;

	// Supplier to allow for lazy evaluation of the filter expression,
	// which may depend on the execution content. For example, you may want to
	// filter dynamically based on the current user's identity or tenant ID.
	private final Supplier<Filter.Expression> filterExpression;

	public VectorStoreDocumentRetriever(VectorStore vectorStore, @Nullable Double similarityThreshold,
			@Nullable Integer topK, @Nullable Supplier<Filter.Expression> filterExpression) {
		Assert.notNull(vectorStore, "vectorStore cannot be null");
		Assert.isTrue(similarityThreshold == null || similarityThreshold >= 0.0,
				"similarityThreshold must be equal to or greater than 0.0");
		Assert.isTrue(topK == null || topK > 0, "topK must be greater than 0");
		this.vectorStore = vectorStore;
		this.similarityThreshold = similarityThreshold != null ? similarityThreshold
				: SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;
		this.topK = topK != null ? topK : SearchRequest.DEFAULT_TOP_K;
		this.filterExpression = filterExpression != null ? filterExpression : () -> null;
	}

	@Override
	public List<Document> retrieve(Query query) {
		Assert.notNull(query, "query cannot be null");
		var requestFilterExpression = computeRequestFilterExpression(query);
		var searchRequest = SearchRequest.builder()
			.query(query.text())
			.filterExpression(requestFilterExpression)
			.similarityThreshold(this.similarityThreshold)
			.topK(this.topK)
			.build();
		return this.vectorStore.similaritySearch(searchRequest);
	}

	/**
	 * Computes the filter expression to use for the current request.
	 * <p>
	 * The filter expression can be provided in the query context using the
	 * {@link #FILTER_EXPRESSION} key. This key accepts either a string representation of
	 * a filter expression or a {@link Filter.Expression} object directly.
	 * <p>
	 * If no filter expression is provided in the context, the default filter expression
	 * configured for this retriever is used.
	 * @param query the query containing potential context with filter expression
	 * @return the filter expression to use for the request
	 */
	private Filter.Expression computeRequestFilterExpression(Query query) {
		var contextFilterExpression = query.context().get(FILTER_EXPRESSION);
		if (contextFilterExpression != null) {
			if (contextFilterExpression instanceof Filter.Expression) {
				return (Filter.Expression) contextFilterExpression;
			}
			else if (StringUtils.hasText(contextFilterExpression.toString())) {
				return new FilterExpressionTextParser().parse(contextFilterExpression.toString());
			}
		}
		return this.filterExpression.get();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link VectorStoreDocumentRetriever}.
	 */
	public static final class Builder {

		private @Nullable VectorStore vectorStore;

		private @Nullable Double similarityThreshold;

		private @Nullable Integer topK;

		private @Nullable Supplier<Filter.Expression> filterExpression;

		private Builder() {
		}

		public Builder vectorStore(VectorStore vectorStore) {
			this.vectorStore = vectorStore;
			return this;
		}

		public Builder similarityThreshold(Double similarityThreshold) {
			this.similarityThreshold = similarityThreshold;
			return this;
		}

		public Builder topK(Integer topK) {
			this.topK = topK;
			return this;
		}

		public Builder filterExpression(Filter.Expression filterExpression) {
			this.filterExpression = () -> filterExpression;
			return this;
		}

		public Builder filterExpression(Supplier<Filter.Expression> filterExpression) {
			this.filterExpression = filterExpression;
			return this;
		}

		public VectorStoreDocumentRetriever build() {
			Assert.state(this.vectorStore != null, "vectorStore cannot be null");
			return new VectorStoreDocumentRetriever(this.vectorStore, this.similarityThreshold, this.topK,
					this.filterExpression);
		}

	}

}
