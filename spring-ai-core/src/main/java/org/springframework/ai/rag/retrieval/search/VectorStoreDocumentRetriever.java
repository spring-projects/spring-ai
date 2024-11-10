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

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Document retriever that uses a vector store to search for documents. It supports
 * filtering based on metadata, similarity threshold, and top-k results.
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
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class VectorStoreDocumentRetriever implements DocumentRetriever {

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
		this.vectorStore = vectorStore;
		this.similarityThreshold = similarityThreshold != null ? similarityThreshold
				: SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;
		this.topK = topK != null ? topK : SearchRequest.DEFAULT_TOP_K;
		this.filterExpression = filterExpression != null ? filterExpression : () -> null;
	}

	@Override
	public List<Document> retrieve(Query query) {
		Assert.notNull(query, "query cannot be null");
		var searchRequest = SearchRequest.query(query.text())
			.withFilterExpression(this.filterExpression.get())
			.withSimilarityThreshold(this.similarityThreshold)
			.withTopK(this.topK);
		return this.vectorStore.similaritySearch(searchRequest);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link VectorStoreDocumentRetriever}.
	 */
	public static final class Builder {

		private VectorStore vectorStore;

		private Double similarityThreshold;

		private Integer topK;

		private Supplier<Filter.Expression> filterExpression;

		private Builder() {
		}

		public Builder vectorStore(VectorStore vectorStore) {
			this.vectorStore = vectorStore;
			return this;
		}

		public Builder similarityThreshold(Double similarityThreshold) {
			Assert.notNull(similarityThreshold, "similarityThreshold cannot be null");
			this.similarityThreshold = similarityThreshold;
			return this;
		}

		public Builder topK(Integer topK) {
			Assert.notNull(topK, "topK cannot be null");
			Assert.isTrue(topK > 0, "topK must be greater than 0");
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
			return new VectorStoreDocumentRetriever(this.vectorStore, this.similarityThreshold, this.topK,
					this.filterExpression);
		}

	}

}
