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
import org.springframework.ai.scoring.ScoringModel;
import org.springframework.ai.scoring.ScoringOptions;
import org.springframework.ai.scoring.ScoringRequest;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.ai.scoring.ScoringResult;
import org.springframework.util.Assert;

/**
 * A {@link DocumentPostProcessor} that uses a {@link ScoringModel} to rerank retrieved
 * documents by their relevance to the query.
 *
 * <p>
 * This processor delegates to any {@link ScoringModel} implementation (e.g., Jina AI
 * Reranker, Cohere Rerank) to score each document against the query, then returns the
 * documents sorted by relevance score in descending order.
 *
 * <p>
 * Example usage: <pre>{@code
 * ScoringDocumentPostProcessor postProcessor = ScoringDocumentPostProcessor.builder()
 *     .scoringModel(jinaScoringModel)
 *     .options(ScoringOptions.builder().topK(5).build())
 *     .build();
 *
 * RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
 *     .documentRetriever(retriever)
 *     .documentPostProcessors(List.of(postProcessor))
 *     .build();
 * }</pre>
 *
 * @author Wongi Kim
 * @since 2.0.0
 * @see ScoringModel
 * @see DocumentPostProcessor
 */
public final class ScoringDocumentPostProcessor implements DocumentPostProcessor {

	private final ScoringModel scoringModel;

	private final @Nullable ScoringOptions defaultOptions;

	/**
	 * Create a new {@link ScoringDocumentPostProcessor}.
	 * @param scoringModel the scoring model to use for reranking
	 * @param defaultOptions the default scoring options, or {@code null} if none
	 */
	public ScoringDocumentPostProcessor(ScoringModel scoringModel, @Nullable ScoringOptions defaultOptions) {
		Assert.notNull(scoringModel, "scoringModel cannot be null");
		this.scoringModel = scoringModel;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public List<Document> process(Query query, List<Document> documents) {
		Assert.notNull(query, "query cannot be null");
		Assert.notNull(documents, "documents cannot be null");

		if (documents.isEmpty()) {
			return documents;
		}

		ScoringRequest request = new ScoringRequest(query.text(), documents, this.defaultOptions);
		ScoringResponse response = this.scoringModel.call(request);

		return response.getResults()
			.stream()
			.map(ScoringResult::getOutput)
			.toList();
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ScoringDocumentPostProcessor}.
	 */
	public static final class Builder {

		private @Nullable ScoringModel scoringModel;

		private @Nullable ScoringOptions options;

		private Builder() {
		}

		/**
		 * Set the {@link ScoringModel} to use for reranking.
		 * @param scoringModel the scoring model
		 * @return this builder
		 */
		public Builder scoringModel(ScoringModel scoringModel) {
			this.scoringModel = scoringModel;
			return this;
		}

		/**
		 * Set the default {@link ScoringOptions}.
		 * @param options the scoring options
		 * @return this builder
		 */
		public Builder options(ScoringOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Build a new {@link ScoringDocumentPostProcessor}.
		 * @return the post processor
		 */
		public ScoringDocumentPostProcessor build() {
			Assert.state(this.scoringModel != null, "scoringModel cannot be null");
			return new ScoringDocumentPostProcessor(this.scoringModel, this.options);
		}

	}

}
