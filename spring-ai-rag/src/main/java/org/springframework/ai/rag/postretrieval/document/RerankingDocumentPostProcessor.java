/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.rag.postretrieval.document;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.util.Assert;

/**
 * A {@link DocumentPostProcessor} that reranks retrieved documents and optionally keeps
 * only the highest-ranked documents.
 *
 * @author KoreaNirsa
 */
public final class RerankingDocumentPostProcessor implements DocumentPostProcessor {

	private final DocumentReranker documentReranker;

	private final @Nullable Integer topN;

	private RerankingDocumentPostProcessor(DocumentReranker documentReranker, @Nullable Integer topN) {
		Assert.notNull(documentReranker, "documentReranker cannot be null");
		if (topN != null) {
			Assert.isTrue(topN > 0, "topN must be greater than 0");
		}
		this.documentReranker = documentReranker;
		this.topN = topN;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public List<Document> process(Query query, List<Document> documents) {
		Assert.notNull(query, "query cannot be null");
		Assert.notNull(documents, "documents cannot be null");
		Assert.noNullElements(documents, "documents cannot contain null elements");

		if (documents.isEmpty()) {
			return List.of();
		}

		List<Document> rerankedDocuments = this.documentReranker.rerank(query, documents);
		Assert.notNull(rerankedDocuments, "rerankedDocuments cannot be null");
		Assert.noNullElements(rerankedDocuments, "rerankedDocuments cannot contain null elements");

		if (this.topN == null || rerankedDocuments.size() <= this.topN) {
			return List.copyOf(rerankedDocuments);
		}

		return List.copyOf(rerankedDocuments.subList(0, this.topN));
	}

	public static final class Builder {

		private @Nullable DocumentReranker documentReranker;

		private @Nullable Integer topN;

		private Builder() {
		}

		/**
		 * Configure the reranker used to reorder retrieved documents.
		 * @param documentReranker the document reranker
		 * @return this builder
		 */
		public Builder documentReranker(DocumentReranker documentReranker) {
			this.documentReranker = documentReranker;
			return this;
		}

		/**
		 * Keep only the first {@code topN} reranked documents.
		 * @param topN the maximum number of reranked documents to keep
		 * @return this builder
		 */
		public Builder topN(int topN) {
			this.topN = topN;
			return this;
		}

		public RerankingDocumentPostProcessor build() {
			Assert.notNull(this.documentReranker, "documentReranker cannot be null");
			return new RerankingDocumentPostProcessor(this.documentReranker, this.topN);
		}

	}

}
