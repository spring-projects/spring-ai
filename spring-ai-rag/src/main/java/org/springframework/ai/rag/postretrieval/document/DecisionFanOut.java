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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.util.Assert;

/**
 * Asks a {@link DecisionModel} the same questions about every retrieved document, one
 * call per document, fanned out over an executor.
 *
 * <p>
 * The post-processors in this package share this shape: the pairs are independent, so
 * there is no honest way to phrase "score each of these forty passages" as a single
 * question, and they fail open, so a failed call has to come back as a value rather than
 * an exception. Each outcome either holds a response in which the model answered every
 * question, or the reason it does not: the call failed, the model refused, or an answer
 * is missing.
 *
 * @author Christian Tzolov
 */
final class DecisionFanOut {

	/** The query field of the input. */
	static final String QUERY_FIELD = "query";

	/** The passage field of the input. */
	static final String PASSAGE_FIELD = "passage";

	private DecisionFanOut() {
	}

	/**
	 * An executor that runs up to the given number of calls at once.
	 * @param concurrency how many calls may be in flight together
	 * @param virtualThreads whether to run the calls on virtual threads, which needs JDK
	 * 21 or later
	 * @param threadNamePrefix the prefix of the thread names
	 * @return the executor
	 * @throws UnsupportedOperationException if virtual threads were asked for on an older
	 * JDK
	 */
	static Executor executor(int concurrency, boolean virtualThreads, String threadNamePrefix) {
		Assert.isTrue(concurrency >= 1, "concurrency must be at least 1");
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
		executor.setConcurrencyLimit(concurrency);
		executor.setVirtualThreads(virtualThreads);
		// Observations and MDC follow the work onto the calling threads, as they do in
		// RetrievalAugmentationAdvisor's own executor.
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		return executor;
	}

	/**
	 * Judges every document against the query. If the calling thread is interrupted, the
	 * documents still waiting come back as failures at once, but calls already in flight
	 * are not cancelled: they run to completion on the executor.
	 * @param decisionModel the model
	 * @param query the query, sent as the {@value #QUERY_FIELD} field
	 * @param documents the documents, each sent as the {@value #PASSAGE_FIELD} field
	 * @param questions the questions asked about each document
	 * @param options the options sent with each prompt, if any
	 * @param executor where the calls run
	 * @return one outcome per document, in the order of the documents
	 */
	static List<Outcome> judge(DecisionModel decisionModel, Query query, List<Document> documents,
			Map<String, ? extends DecisionQuestion> questions, @Nullable DecisionOptions options, Executor executor) {
		List<CompletableFuture<DecisionResponse>> futures = new ArrayList<>(documents.size());
		for (Document document : documents) {
			DecisionPrompt prompt = prompt(query, document, questions, options);
			futures.add(submit(() -> decisionModel.call(prompt), executor));
		}
		List<Outcome> outcomes = new ArrayList<>(documents.size());
		for (CompletableFuture<DecisionResponse> future : futures) {
			outcomes.add(outcome(future, questions));
		}
		return outcomes;
	}

	private static DecisionPrompt prompt(Query query, Document document,
			Map<String, ? extends DecisionQuestion> questions, @Nullable DecisionOptions options) {
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(QUERY_FIELD, query.text());
		String text = document.getText();
		fields.put(PASSAGE_FIELD, (text != null) ? text : "");
		return DecisionPrompt.builder().fields(fields).questions(questions).options(options).build();
	}

	private static CompletableFuture<DecisionResponse> submit(Supplier<DecisionResponse> call, Executor executor) {
		try {
			return CompletableFuture.supplyAsync(call, executor);
		}
		catch (RuntimeException ex) {
			// A saturated pool rejects the submission; that is this document's failure,
			// not the request's.
			return CompletableFuture.failedFuture(ex);
		}
	}

	private static Outcome outcome(CompletableFuture<DecisionResponse> future,
			Map<String, ? extends DecisionQuestion> questions) {
		DecisionResponse response;
		try {
			response = future.get();
		}
		catch (InterruptedException ex) {
			// The caller gave up on the request; leave the flag set so the remaining
			// futures fall through here at once instead of being waited for.
			Thread.currentThread().interrupt();
			return new Outcome(null, "interrupted while waiting for the decision", null);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof Error error) {
				throw error;
			}
			RuntimeException failure = (cause instanceof RuntimeException runtime) ? runtime
					: new IllegalStateException(cause);
			return new Outcome(null, String.valueOf(failure), failure);
		}
		for (String name : questions.keySet()) {
			if (!response.hasAnswer(name)) {
				return new Outcome(null, "no answer to '" + name + "'", null);
			}
			if (response.getAnswer(name) instanceof RefusalAnswer refusal) {
				return new Outcome(null, "refused '" + name + "': " + refusal.getReason(), null);
			}
		}
		return new Outcome(response, null, null);
	}

	/**
	 * What one call came back with.
	 *
	 * @param response the response, when the model answered every question
	 * @param reason why there is no response: the failure, or the refusal
	 * @param failure the exception that stopped the call, when one did
	 */
	record Outcome(@Nullable DecisionResponse response, @Nullable String reason, @Nullable RuntimeException failure) {

	}

}
