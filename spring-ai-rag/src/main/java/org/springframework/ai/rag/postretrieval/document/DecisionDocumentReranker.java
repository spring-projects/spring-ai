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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.util.Assert;

/**
 * Reorders retrieved documents by asking a {@link DecisionModel}, one document at a time,
 * whether each could actually answer the query.
 *
 * <p>
 * Vector search ranks by embedding proximity, which is a good way to find candidates and
 * a poor way to order them: a passage can be about the right subject and still not
 * contain the answer. Asking a direct question about each candidate is a different
 * measurement, and it is the one the final ordering should use.
 *
 * <p>
 * This is one call per document, since the pairs are independent. The calls are fanned
 * out concurrently; {@link Builder#concurrency(int)} or
 * {@link Builder#executor(Executor)} controls how widely.
 *
 * <p>
 * Each scored document carries its score under {@link #SCORE_METADATA_KEY}, so a later
 * stage or a log can see why the order came out as it did. A document whose call failed,
 * or that the model refused to judge, is kept rather than dropped, because a failure is
 * not evidence about relevance. It is placed after every document this reranker actually
 * scored, so a failure can never evict a known-good passage under {@code topK}.
 *
 * <pre>{@code
 * RetrievalAugmentationAdvisor.builder()
 *     .documentRetriever(retriever)
 *     .documentPostProcessors(DecisionDocumentReranker.builder(decisionModel).topK(5).build())
 *     .build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class DecisionDocumentReranker implements DocumentPostProcessor {

	/** Metadata key holding the score this reranker gave a document. */
	public static final String SCORE_METADATA_KEY = "decision.rerank.score";

	/** The name of the question asked about each document. */
	public static final String QUESTION_NAME = "answers_query";

	/** The query field of the input. */
	public static final String QUERY_FIELD = DecisionFanOut.QUERY_FIELD;

	/** The passage field of the input. */
	public static final String PASSAGE_FIELD = DecisionFanOut.PASSAGE_FIELD;

	/** The question asked unless {@link Builder#question(YesNoQuestion)} replaces it. */
	public static final YesNoQuestion DEFAULT_QUESTION = YesNoQuestion.builder()
		.text("Could the `passage` answer the `query`? Judge whether the passage states information "
				+ "that answers the query, not merely whether it covers the same subject.")
		.whenYes("The passage contains information that answers the query")
		.whenNo("The passage is about something else, or mentions the subject without answering")
		.build();

	private static final Logger logger = LoggerFactory.getLogger(DecisionDocumentReranker.class);

	private final DecisionModel decisionModel;

	private final YesNoQuestion question;

	private final @Nullable DecisionOptions options;

	private final @Nullable Integer topK;

	private final double minimumScore;

	private final Executor executor;

	private DecisionDocumentReranker(DecisionModel decisionModel, YesNoQuestion question,
			@Nullable DecisionOptions options, @Nullable Integer topK, double minimumScore, Executor executor) {
		this.decisionModel = decisionModel;
		this.question = question;
		this.options = options;
		this.topK = topK;
		this.minimumScore = minimumScore;
		this.executor = executor;
	}

	@Override
	public List<Document> process(Query query, List<Document> documents) {
		Assert.notNull(query, "query must not be null");
		Assert.notNull(documents, "documents must not be null");
		if (documents.isEmpty()) {
			return List.of();
		}

		List<DecisionFanOut.Outcome> outcomes = DecisionFanOut.judge(this.decisionModel, query, documents,
				Map.of(QUESTION_NAME, this.question), this.options, this.executor);

		List<Scored> scored = new ArrayList<>(documents.size());
		int unscored = 0;
		DecisionFanOut.Outcome firstMiss = null;
		for (int i = 0; i < documents.size(); i++) {
			DecisionFanOut.Outcome outcome = outcomes.get(i);
			// A failed call says nothing about the document, so it is recorded as unknown
			// rather than as zero, which would read as "judged irrelevant". A refusal
			// counts the same: the model declined to judge, it did not judge against.
			DecisionResponse response = outcome.response();
			Double score = (response != null) ? response.getYesNo(QUESTION_NAME).getProbability() : null;
			if (score == null) {
				unscored++;
				if (firstMiss == null) {
					firstMiss = outcome;
				}
			}
			scored.add(new Scored(i, documents.get(i), score));
		}
		if (firstMiss != null) {
			// Not silently: when every call fails this is the retriever's order again.
			logger.warn("Could not score {} of {} documents, keeping them unscored after the scored ones: {}", unscored,
					documents.size(), firstMiss.reason());
			if (firstMiss.failure() != null) {
				logger.debug("The first decision call that failed", firstMiss.failure());
			}
		}

		List<Scored> kept = scored.stream()
			.filter(entry -> entry.score == null || entry.score >= this.minimumScore)
			.toList();

		// Scored documents first, in descending order; then the unscored ones, in the
		// order they arrived in. An unscored document is kept, but it does not outrank
		// one this reranker actually judged, so under topK it can never evict a
		// known-good passage.
		List<Document> reordered = new ArrayList<>(kept.size());
		kept.stream()
			.filter(entry -> entry.score() != null)
			.sorted(Comparator.comparingDouble((Scored entry) -> entry.score())
				.reversed()
				.thenComparingInt(Scored::originalIndex))
			.forEach(entry -> reordered.add(entry.withScoreMetadata()));
		kept.stream().filter(entry -> entry.score() == null).forEach(entry -> reordered.add(entry.document()));

		int limit = (this.topK == null) ? reordered.size() : Math.min(this.topK, reordered.size());
		return List.copyOf(reordered.subList(0, limit));
	}

	/**
	 * @param decisionModel the model that judges the documents
	 * @return a new builder
	 */
	public static Builder builder(DecisionModel decisionModel) {
		return new Builder(decisionModel);
	}

	private record Scored(int originalIndex, Document document, @Nullable Double score) {

		/**
		 * A copy carrying the score, rather than a write into the retriever's own
		 * instance: a document dropped by {@code topK} must not be left wearing a score,
		 * and a cached document reused across two runs must not keep the first run's.
		 *
		 * <p>
		 * The map is rebuilt explicitly because {@link Document#mutate()} hands the
		 * builder the <em>same</em> metadata map, so {@code metadata(key, value)} would
		 * write straight back into the original document.
		 */
		Document withScoreMetadata() {
			Assert.state(this.score != null, "only scored documents carry a score");
			Map<String, Object> metadata = new LinkedHashMap<>(this.document.getMetadata());
			metadata.put(SCORE_METADATA_KEY, this.score);
			return this.document.mutate().metadata(metadata).build();
		}

	}

	/**
	 * Builder for {@link DecisionDocumentReranker}.
	 *
	 * @since 2.1.0
	 */
	public static final class Builder {

		/** How many documents are judged at once unless an executor is given. */
		public static final int DEFAULT_CONCURRENCY = 4;

		private final DecisionModel decisionModel;

		private YesNoQuestion question = DEFAULT_QUESTION;

		private @Nullable DecisionOptions options;

		private @Nullable Integer topK;

		private double minimumScore = 0.0d;

		private int concurrency = DEFAULT_CONCURRENCY;

		private boolean virtualThreads;

		private @Nullable Executor executor;

		private Builder(DecisionModel decisionModel) {
			Assert.notNull(decisionModel, "decisionModel must not be null");
			this.decisionModel = decisionModel;
		}

		/**
		 * Replaces the relevance question. Write it about {@code query} and
		 * {@code passage}, which are the two fields of the input.
		 * @param question the question
		 * @return this builder
		 */
		public Builder question(YesNoQuestion question) {
			Assert.notNull(question, "question must not be null");
			this.question = question;
			return this;
		}

		/**
		 * Options sent with every prompt, such as the model to use.
		 * @param options the options, or {@code null} for the model's defaults
		 * @return this builder
		 */
		public Builder options(@Nullable DecisionOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * @param topK how many documents to keep after reordering
		 * @return this builder
		 */
		public Builder topK(int topK) {
			Assert.isTrue(topK >= 1, "topK must be at least 1");
			this.topK = topK;
			return this;
		}

		/**
		 * Drops documents scoring below a bar. Left at zero, nothing is dropped and this
		 * is purely a reordering. Unscored documents are never dropped by it.
		 * @param minimumScore the lowest score worth keeping, between 0 and 1
		 * @return this builder
		 */
		public Builder minimumScore(double minimumScore) {
			Assert.isTrue(minimumScore >= 0.0d && minimumScore <= 1.0d, "minimumScore must be between 0 and 1");
			this.minimumScore = minimumScore;
			return this;
		}

		/**
		 * How many documents to judge at once. The limit belongs to this instance, so it
		 * caps the calls in flight across all requests that pass through it, which is
		 * what a provider rate limit needs. Ignored when an {@link #executor(Executor)
		 * executor} is given, which then sets its own limit.
		 * @param concurrency the number of calls in flight together, at least 1
		 * @return this builder
		 */
		public Builder concurrency(int concurrency) {
			Assert.isTrue(concurrency >= 1, "concurrency must be at least 1");
			this.concurrency = concurrency;
			return this;
		}

		/**
		 * Runs the per-document calls on virtual threads instead of platform threads,
		 * keeping the {@link #concurrency(int) concurrency limit}. Needs JDK 21 or later;
		 * on an older JDK {@link #build()} fails. Ignored when an
		 * {@link #executor(Executor) executor} is given.
		 * @param virtualThreads whether to use virtual threads
		 * @return this builder
		 */
		public Builder virtualThreads(boolean virtualThreads) {
			this.virtualThreads = virtualThreads;
			return this;
		}

		/**
		 * Where the per-document calls run, for example a pool shared with the rest of
		 * the application or one backed by virtual threads.
		 * @param executor the executor
		 * @return this builder
		 */
		public Builder executor(Executor executor) {
			Assert.notNull(executor, "executor must not be null");
			this.executor = executor;
			return this;
		}

		public DecisionDocumentReranker build() {
			Executor executor = (this.executor != null) ? this.executor
					: DecisionFanOut.executor(this.concurrency, this.virtualThreads, "decision-rerank-");
			return new DecisionDocumentReranker(this.decisionModel, this.question, this.options, this.topK,
					this.minimumScore, executor);
		}

	}

}
