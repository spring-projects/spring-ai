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

import java.time.Duration;
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
 * whether each one answers the query.
 *
 * <p>
 * Vector search ranks by embedding distance. That is a good way to find candidates and a
 * poor way to order them, because a passage can be on the right subject and still not
 * contain the answer. Asking the model directly whether each candidate answers the query
 * measures something else, and that is what the final order should be based on.
 *
 * <p>
 * Each document is judged in its own call, because the query and passage pairs are
 * independent. The calls run concurrently; {@link Builder#concurrency(int)} or
 * {@link Builder#executor(Executor)} controls how many at once.
 *
 * <p>
 * Each scored document carries its score under {@link #SCORE_METADATA_KEY}, so a later
 * stage or a log can see why the order came out this way. A document whose call failed or
 * timed out, or that the model refused to judge, is kept rather than dropped, because a
 * failure says nothing about relevance. It is placed after all the scored documents, so a
 * failure can never push a known-good passage out under {@code topK}.
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

	private final Duration timeout;

	private final Executor executor;

	private DecisionDocumentReranker(DecisionModel decisionModel, YesNoQuestion question,
			@Nullable DecisionOptions options, @Nullable Integer topK, double minimumScore, Duration timeout,
			Executor executor) {
		this.decisionModel = decisionModel;
		this.question = question;
		this.options = options;
		this.topK = topK;
		this.minimumScore = minimumScore;
		this.timeout = timeout;
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
				Map.of(QUESTION_NAME, this.question), this.options, this.timeout, this.executor);

		List<Scored> scored = new ArrayList<>(documents.size());
		int unscored = 0;
		DecisionFanOut.Outcome firstMiss = null;
		String firstMissReason = null;
		for (int i = 0; i < documents.size(); i++) {
			DecisionFanOut.Outcome outcome = outcomes.get(i);
			// A failed call says nothing about the document, so its score is recorded as
			// unknown rather than zero, which would look like "judged irrelevant". A
			// refusal counts the same way: the model declined to judge, it did not judge
			// against.
			String unanswered = outcome.unanswered(QUESTION_NAME);
			Double score = null;
			if (unanswered == null) {
				DecisionResponse response = outcome.response();
				Assert.state(response != null, "an answered outcome has a response");
				score = response.getYesNo(QUESTION_NAME).getProbability();
			}
			else {
				unscored++;
				if (firstMiss == null) {
					firstMiss = outcome;
					firstMissReason = unanswered;
				}
			}
			scored.add(new Scored(i, documents.get(i), score));
		}
		if (firstMiss != null) {
			// Say so: when every call fails, the result is just the retriever's order.
			logger.warn("Could not score {} of {} documents, keeping them unscored after the scored ones: {}", unscored,
					documents.size(), firstMissReason);
			if (firstMiss.failure() != null) {
				logger.debug("The first decision call that failed", firstMiss.failure());
			}
		}

		List<Scored> kept = scored.stream()
			.filter(entry -> entry.score == null || entry.score >= this.minimumScore)
			.toList();

		// Scored documents first, highest score first; then the unscored ones, in the
		// order they arrived. An unscored document is kept, but it never outranks one
		// the model actually judged, so under topK it can never push a known-good
		// passage out.
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
		 * Returns a copy with the score instead of writing into the retriever's own
		 * document: a document dropped by {@code topK} must not keep a score, and a
		 * cached document reused in a later run must not keep the earlier score.
		 *
		 * <p>
		 * The metadata map is copied by hand because {@link Document#mutate()} gives the
		 * builder the same map instance, so {@code metadata(key, value)} would write into
		 * the original document.
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

		/** The timeout unless one is given. */
		public static final Duration DEFAULT_TIMEOUT = DecisionFanOut.DEFAULT_TIMEOUT;

		private final DecisionModel decisionModel;

		private YesNoQuestion question = DEFAULT_QUESTION;

		private @Nullable DecisionOptions options;

		private @Nullable Integer topK;

		private double minimumScore = 0.0d;

		private Duration timeout = DEFAULT_TIMEOUT;

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
		 * Drops documents whose score is below this value. At zero nothing is dropped and
		 * the reranker only reorders. Unscored documents are never dropped by it.
		 * @param minimumScore the lowest score worth keeping, between 0 and 1
		 * @return this builder
		 */
		public Builder minimumScore(double minimumScore) {
			Assert.isTrue(minimumScore >= 0.0d && minimumScore <= 1.0d, "minimumScore must be between 0 and 1");
			this.minimumScore = minimumScore;
			return this;
		}

		/**
		 * How long one request waits for all its documents to be judged, no matter how
		 * many there are. Documents still unjudged when the time is up are kept unscored,
		 * after the scored ones. Calls already running finish; calls still waiting in the
		 * queue are skipped. If you supply your own {@link #executor(Executor) executor},
		 * it must not block when a call is submitted, because that blocked time cannot be
		 * cut short.
		 * @param timeout the timeout, positive
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "timeout must not be null");
			Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
			this.timeout = timeout;
			return this;
		}

		/**
		 * How many documents to judge at the same time. The limit is per instance, so it
		 * caps the calls in flight across all requests going through this reranker, which
		 * is what a provider rate limit needs. Ignored when an {@link #executor(Executor)
		 * executor} is given; the executor then sets its own limit.
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
					this.minimumScore, this.timeout, executor);
		}

	}

}
