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

package org.springframework.ai.voyageai;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Reorders retrieved documents with a Voyage AI reranker.
 *
 * <p>
 * The reranker scores every document against the query in one API call and returns the
 * documents most relevant first. The relevance score replaces the document's score, so
 * {@link Document#getScore()} holds it. {@link Builder#topK(int)} keeps only the best
 * documents. A request can rerank at most {@value VoyageAiApi#MAX_INPUTS_PER_REQUEST}
 * documents; more are rejected instead of being cut off silently.
 *
 * <p>
 * The model is never defaulted. This is independent of the
 * {@code DecisionDocumentReranker}, which asks a chat model instead.
 *
 * <pre>{@code
 * RetrievalAugmentationAdvisor.builder()
 *     .documentRetriever(retriever)
 *     .documentPostProcessors(VoyageAiDocumentReranker.builder()
 *         .voyageAiApi(voyageAiApi)
 *         .model(VoyageAiApi.RerankModel.RERANK_3)
 *         .topK(5)
 *         .build())
 *     .build();
 * }</pre>
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public final class VoyageAiDocumentReranker implements DocumentPostProcessor {

	private final VoyageAiApi voyageAiApi;

	private final String model;

	private final @Nullable Integer topK;

	private final @Nullable Boolean truncation;

	private final RetryTemplate retryTemplate;

	private VoyageAiDocumentReranker(VoyageAiApi voyageAiApi, String model, @Nullable Integer topK,
			@Nullable Boolean truncation, RetryTemplate retryTemplate) {
		this.voyageAiApi = voyageAiApi;
		this.model = model;
		this.topK = topK;
		this.truncation = truncation;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public List<Document> process(Query query, List<Document> documents) {
		Assert.notNull(query, "query must not be null");
		Assert.notNull(documents, "documents must not be null");
		if (documents.isEmpty()) {
			return List.of();
		}
		List<String> texts = documents.stream().map(document -> {
			String text = document.getText();
			Assert.hasText(text, () -> "Document " + document.getId() + " has no text to rerank");
			return text;
		}).toList();

		var request = new VoyageAiApi.RerankRequest(query.text(), texts, this.model, this.topK, Boolean.TRUE,
				this.truncation);
		var response = RetryUtils.execute(this.retryTemplate, () -> this.voyageAiApi.rerank(request));
		VoyageAiResponseSupport.requireTotalTokens(response.usage(), "rerank");

		List<Document> ranked = new ArrayList<>(response.data().size());
		for (VoyageAiApi.RerankResult result : response.data()) {
			Assert.state(result.index() >= 0 && result.index() < documents.size(),
					() -> "Voyage AI returned the document index " + result.index() + " for " + documents.size()
							+ " documents");
			ranked.add(documents.get(result.index()).mutate().score(result.relevanceScore()).build());
		}
		return ranked;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable VoyageAiApi voyageAiApi;

		private @Nullable String model;

		private @Nullable Integer topK;

		private @Nullable Boolean truncation;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		public Builder voyageAiApi(VoyageAiApi voyageAiApi) {
			this.voyageAiApi = voyageAiApi;
			return this;
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder model(VoyageAiApi.RerankModel model) {
			this.model = model.getValue();
			return this;
		}

		/**
		 * Keep only the {@code topK} most relevant documents. All are kept when unset.
		 * @param topK how many documents to keep, positive
		 * @return this builder
		 */
		public Builder topK(int topK) {
			Assert.isTrue(topK > 0, "topK must be positive");
			this.topK = topK;
			return this;
		}

		public Builder truncation(boolean truncation) {
			this.truncation = truncation;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public VoyageAiDocumentReranker build() {
			Assert.state(this.voyageAiApi != null, "VoyageAiApi must not be null");
			String resolved = VoyageAiApi.RerankModel.fromValue(this.model).getValue();
			return new VoyageAiDocumentReranker(this.voyageAiApi, resolved, this.topK, this.truncation,
					this.retryTemplate);
		}

	}

}
