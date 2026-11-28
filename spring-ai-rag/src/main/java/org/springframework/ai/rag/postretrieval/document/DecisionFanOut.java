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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

/**
 * Asks a {@link DecisionModel} the same questions about every retrieved document, one
 * call per document, running the calls on an executor.
 *
 * <p>
 * The post-processors in this package share two traits. The query and passage pairs are
 * independent, so each document gets its own call rather than one call about all of them.
 * And they fail open, so a failed call comes back as a value rather than an exception. An
 * {@link Outcome} holds the response when the call returned one, including any refusals,
 * so the caller can use the answers it did get; otherwise it holds the reason there is
 * none.
 *
 * @author Christian Tzolov
 */
final class DecisionFanOut {

	/** The query field of the input. */
	static final String QUERY_FIELD = "query";

	/** The passage field of the input. */
	static final String PASSAGE_FIELD = "passage";

	/** The timeout unless one is given. */
	static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

	/** How long an idle thread of a default executor is kept, in seconds. */
	private static final int KEEP_ALIVE_SECONDS = 60;

	/**
	 * The longest timeout used to compute a deadline. Anything longer is as good as no
	 * timeout, and {@code System.nanoTime()} arithmetic breaks for spans close to
	 * {@code Long.MAX_VALUE} nanoseconds.
	 */
	private static final Duration MAX_TIMEOUT = Duration.ofDays(365);

	private DecisionFanOut() {
	}

	/**
	 * An executor that runs up to the given number of calls at once and queues the rest.
	 * Submitting a call never blocks, which is what lets {@link #judge} keep its deadline
	 * no matter how many documents a request has. The threads are daemon threads that
	 * exit after a minute idle, so the executor needs no shutdown.
	 * @param concurrency how many calls may run at the same time
	 * @param virtualThreads whether to run the calls on virtual threads, which needs JDK
	 * 21 or later
	 * @param threadNamePrefix the prefix of the thread names
	 * @return the executor
	 * @throws UnsupportedOperationException if virtual threads were asked for on an older
	 * JDK
	 */
	static Executor executor(int concurrency, boolean virtualThreads, String threadNamePrefix) {
		Assert.isTrue(concurrency >= 1, "concurrency must be at least 1");
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setCorePoolSize(concurrency);
		executor.setMaxPoolSize(concurrency);
		executor.setAllowCoreThreadTimeOut(true);
		executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
		executor.setDaemon(true);
		executor.setVirtualThreads(virtualThreads);
		// Observations and MDC follow the work onto the calling threads, as they do in
		// RetrievalAugmentationAdvisor's own executor.
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.initialize();
		return executor;
	}

	/**
	 * Judges every document against the query. The timeout covers the whole batch and
	 * starts before the first call is submitted. When it expires, or when the calling
	 * thread is interrupted, the documents not yet judged come back as failures right
	 * away. Calls already running finish on the executor; calls still queued are skipped.
	 * @param decisionModel the model
	 * @param query the query, sent as the {@value #QUERY_FIELD} field
	 * @param documents the documents, each sent as the {@value #PASSAGE_FIELD} field
	 * @param questions the questions asked about each document
	 * @param options the options sent with each prompt, if any
	 * @param timeout how long to wait for all the documents to be judged
	 * @param executor where the calls run
	 * @return one outcome per document, in the order of the documents
	 */
	static List<Outcome> judge(DecisionModel decisionModel, Query query, List<Document> documents,
			Map<String, ? extends DecisionQuestion> questions, @Nullable DecisionOptions options, Duration timeout,
			Executor executor) {
		long deadline = System.nanoTime() + nanos(timeout);
		List<CompletableFuture<DecisionResponse>> futures = new ArrayList<>(documents.size());
		for (Document document : documents) {
			DecisionPrompt prompt = prompt(query, document, questions, options);
			futures.add(submit(() -> {
				if (System.nanoTime() - deadline >= 0) {
					// Nobody is waiting for this any more: the caller has recorded the
					// document as timed out, so spare the provider the call.
					throw new DeadlinePassed();
				}
				return decisionModel.call(prompt);
			}, executor));
		}
		List<Outcome> outcomes = new ArrayList<>(documents.size());
		for (CompletableFuture<DecisionResponse> future : futures) {
			outcomes.add(outcome(future, deadline, timeout));
		}
		return outcomes;
	}

	/**
	 * Builds the prompt for one document. A document holds either text or media; media is
	 * sent as a content part so that a multimodal decision model can look at it.
	 */
	private static DecisionPrompt prompt(Query query, Document document,
			Map<String, ? extends DecisionQuestion> questions, @Nullable DecisionOptions options) {
		Media media = document.getMedia();
		String text = document.getText();
		Object passage = (media != null) ? List.of(MediaPart.of(media)) : (text != null) ? text : "";
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(QUERY_FIELD, query.text());
		fields.put(PASSAGE_FIELD, passage);
		return DecisionPrompt.builder().fields(fields).questions(questions).options(options).build();
	}

	private static long nanos(Duration timeout) {
		return ((timeout.compareTo(MAX_TIMEOUT) > 0) ? MAX_TIMEOUT : timeout).toNanos();
	}

	private static CompletableFuture<DecisionResponse> submit(Supplier<DecisionResponse> call, Executor executor) {
		try {
			return CompletableFuture.supplyAsync(call, executor);
		}
		catch (RuntimeException ex) {
			// A full pool rejects the submission. That counts as a failure of this
			// document, not of the whole request.
			return CompletableFuture.failedFuture(ex);
		}
	}

	private static Outcome outcome(CompletableFuture<DecisionResponse> future, long deadline, Duration timeout) {
		DecisionResponse response;
		try {
			response = future.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException ex) {
			// The deadline is shared by the batch, so once it has passed the remaining
			// futures fall through here without waiting.
			return timedOut(timeout);
		}
		catch (InterruptedException ex) {
			// The caller gave up on the request. Keep the interrupt flag set so the
			// remaining futures end up here at once instead of being waited for.
			Thread.currentThread().interrupt();
			return new Outcome(null, "interrupted while waiting for the decision", null);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof Error error) {
				throw error;
			}
			if (cause instanceof DeadlinePassed) {
				// Dequeued after the deadline but before the caller gave up on it:
				// still a timeout, not a failed call.
				return timedOut(timeout);
			}
			RuntimeException failure = (cause instanceof RuntimeException runtime) ? runtime
					: new IllegalStateException(cause);
			return new Outcome(null, String.valueOf(failure), failure);
		}
		return new Outcome(response, null, null);
	}

	private static Outcome timedOut(Duration timeout) {
		return new Outcome(null, "timed out after " + timeout.toMillis() + " ms waiting for the documents to be judged",
				null);
	}

	/**
	 * Completes the future of a call that was skipped because the deadline had passed.
	 * Nothing catches it, so it carries no stack trace.
	 */
	private static final class DeadlinePassed extends RuntimeException {

		DeadlinePassed() {
			super("the deadline passed before the call started", null, false, false);
		}

	}

	/**
	 * What one call came back with.
	 *
	 * @param response the response, if the call returned one; it may still be missing
	 * answers or contain refusals, which {@link #unanswered(String)} reports per question
	 * @param reason why there is no response: the failure, the timeout, or the
	 * interruption
	 * @param failure the exception that stopped the call, if there was one
	 */
	record Outcome(@Nullable DecisionResponse response, @Nullable String reason, @Nullable RuntimeException failure) {

		/**
		 * Why the named question has no usable answer: the call returned no response, the
		 * response has no answer for it, or the model refused it. A wrong answer type
		 * cannot happen, because {@link DecisionResponse} checks every answer against its
		 * question when it is built.
		 * @param name the name of the question
		 * @return the reason, or {@code null} when the question was answered
		 */
		@Nullable String unanswered(String name) {
			if (this.response == null) {
				return this.reason;
			}
			if (!this.response.hasAnswer(name)) {
				return "no answer to '" + name + "'";
			}
			if (this.response.getAnswer(name) instanceof RefusalAnswer refusal) {
				return "refused '" + name + "': " + refusal.getReason();
			}
			return null;
		}

	}

}
