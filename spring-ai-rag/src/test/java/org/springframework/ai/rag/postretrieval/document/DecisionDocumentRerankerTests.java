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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DecisionDocumentReranker}. The model is a stub that scores each
 * document from the number in its id, so the calls can run in any order.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class DecisionDocumentRerankerTests {

	private final Query query = new Query("How are refresh tokens rotated?");

	@Test
	void reordersDocumentsByHowWellTheyAnswerTheQuery() {
		// doc-1 scores 0.1, doc-7 scores 0.7, doc-9 scores 0.9.
		List<Document> reranked = DecisionDocumentReranker.builder(scoring(index -> index / 10.0d))
			.build()
			.process(this.query, documents(1, 7, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-7", "doc-1");
		assertThat(reranked.get(0).getMetadata()).containsEntry(DecisionDocumentReranker.SCORE_METADATA_KEY, 0.9d);
	}

	@Test
	void keepsOnlyTheTopK() {
		List<Document> reranked = DecisionDocumentReranker.builder(scoring(index -> index / 10.0d))
			.topK(2)
			.build()
			.process(this.query, documents(1, 7, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-7");
	}

	@Test
	void dropsDocumentsBelowTheMinimumScore() {
		List<Document> reranked = DecisionDocumentReranker.builder(scoring(index -> index / 10.0d))
			.minimumScore(0.5d)
			.build()
			.process(this.query, documents(1, 7, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-7");
	}

	@Test
	void aDocumentWhoseCallFailedIsKeptBelowTheScoredOnesRatherThanDropped() {
		// A transport failure is not evidence that a passage is irrelevant, so losing it
		// would silently shrink the context on an unrelated error.
		List<Document> reranked = DecisionDocumentReranker.builder(failingFor(7, index -> 0.1d))
			.build()
			.process(this.query, documents(1, 7));

		// Order, not just membership: doc-7's call failed, so it sits below the document
		// this reranker actually judged.
		assertThat(reranked).extracting(Document::getId).containsExactly("doc-1", "doc-7");
		assertThat(reranked.get(1).getMetadata()).doesNotContainKey(DecisionDocumentReranker.SCORE_METADATA_KEY);
	}

	@Test
	void unscoredDocumentsSinkBelowEveryScoredOne() {
		// doc-2 fails; doc-1 and doc-9 score 0.1 and 0.9. The two scored documents come
		// first in score order, and the unjudged one follows, even though it arrived
		// between them.
		List<Document> reranked = DecisionDocumentReranker.builder(failingFor(2, index -> index / 10.0d))
			.build()
			.process(this.query, documents(1, 2, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-1", "doc-2");
	}

	@Test
	void anUnscoredDocumentDoesNotEvictABetterOneUnderTopK() {
		List<Document> reranked = DecisionDocumentReranker.builder(failingFor(2, index -> index / 10.0d))
			.topK(2)
			.build()
			.process(this.query, documents(2, 1, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-1");
	}

	@Test
	void aRefusalLeavesTheDocumentUnscoredRatherThanFailingTheRequest(CapturedOutput output) {
		DecisionModel refusingSeven = prompt -> {
			int index = indexOf(prompt);
			DecisionAnswer answer = (index == 7) ? RefusalAnswer.builder().reason("policy").build()
					: YesNoAnswer.builder().probability(index / 10.0d).build();
			return DecisionResponse.builder(prompt).answer(DecisionDocumentReranker.QUESTION_NAME, answer).build();
		};

		List<Document> reranked = DecisionDocumentReranker.builder(refusingSeven)
			.build()
			.process(this.query, documents(7, 1));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-1", "doc-7");
		assertThat(output).contains("Could not score 1 of 2 documents").contains("refused 'answers_query': policy");
	}

	@Test
	void unscoredDocumentsAreNeverDroppedByTheMinimumScore() {
		List<Document> reranked = DecisionDocumentReranker.builder(failingFor(7, index -> 0.9d))
			.minimumScore(0.5d)
			.build()
			.process(this.query, documents(1, 7));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-1", "doc-7");
	}

	@Test
	void saysSoWhenDocumentsCouldNotBeScored(CapturedOutput output) {
		// Failing open is the right call, but not silently: with a bad key or an outage
		// the result would otherwise look as if it had been reranked.
		DecisionModel broken = prompt -> {
			throw new IllegalStateException("boom");
		};

		List<Document> reranked = DecisionDocumentReranker.builder(broken).build().process(this.query, documents(1, 7));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-1", "doc-7");
		assertThat(output).contains("Could not score 2 of 2 documents, keeping them unscored after the scored ones")
			.contains("boom");
	}

	@Test
	void scoringDoesNotMutateTheRetrieversOwnDocuments() {
		List<Document> original = documents(1, 9);

		List<Document> reranked = DecisionDocumentReranker.builder(scoring(index -> index / 10.0d))
			.topK(1)
			.build()
			.process(this.query, original);

		assertThat(reranked).hasSize(1);
		assertThat(original).allSatisfy(document -> assertThat(document.getMetadata())
			.doesNotContainKey(DecisionDocumentReranker.SCORE_METADATA_KEY));
	}

	@Test
	void sendsTheQueryAndThePassageWithTheDefaultQuestion() {
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return yes(prompt, 0.5d);
		};

		DecisionDocumentReranker.builder(recording).build().process(this.query, documents(3));

		assertThat(prompts).hasSize(1);
		DecisionPrompt prompt = prompts.get(0);
		assertThat(prompt.getInstructions()).isInstanceOfSatisfying(FieldsInput.class,
				input -> assertThat(input.getFields()).containsExactly(
						Map.entry(DecisionDocumentReranker.QUERY_FIELD, "How are refresh tokens rotated?"),
						Map.entry(DecisionDocumentReranker.PASSAGE_FIELD, "passage doc-3")));
		assertThat(prompt.getQuestions()).containsExactly(
				Map.entry(DecisionDocumentReranker.QUESTION_NAME, DecisionDocumentReranker.DEFAULT_QUESTION));
		assertThat(prompt.getOptions()).isNull();
	}

	@Test
	void sendsACustomQuestionAndOptions() {
		YesNoQuestion question = YesNoQuestion.of("Is the `passage` about the `query`?");
		DecisionOptions options = DecisionOptions.builder().model("judge-1").build();
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return yes(prompt, 0.5d);
		};

		DecisionDocumentReranker.builder(recording)
			.question(question)
			.options(options)
			.build()
			.process(this.query, documents(3));

		assertThat(prompts).hasSize(1);
		assertThat(prompts.get(0).getQuestions()).containsEntry(DecisionDocumentReranker.QUESTION_NAME, question);
		assertThat(prompts.get(0).getOptions()).isEqualTo(options);
	}

	@Test
	void aDocumentWithoutTextIsSentAsAnEmptyPassage() {
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return yes(prompt, 0.5d);
		};

		DecisionDocumentReranker.builder(recording).build().process(this.query, List.of(mediaOnly("doc-1")));

		assertThat(prompts).singleElement()
			.extracting(DecisionPrompt::getInstructions)
			.isInstanceOfSatisfying(FieldsInput.class,
					input -> assertThat(input.getFields()).containsEntry(DecisionDocumentReranker.PASSAGE_FIELD, ""));
	}

	@Test
	void aRejectedSubmissionLeavesTheDocumentUnscoredRatherThanFailingTheRequest(CapturedOutput output) {
		// A saturated pool rejects the submission outright, before any call is made.
		Executor rejectingSecond = new Executor() {
			private int submitted;

			@Override
			public void execute(Runnable task) {
				if (++this.submitted == 2) {
					throw new RejectedExecutionException("pool is full");
				}
				task.run();
			}
		};

		List<Document> reranked = DecisionDocumentReranker.builder(scoring(index -> index / 10.0d))
			.executor(rejectingSecond)
			.build()
			.process(this.query, documents(1, 7, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-1", "doc-7");
		assertThat(output).contains("Could not score 1 of 3 documents").contains("pool is full");
	}

	@Test
	void anInterruptedCallerGetsTheDocumentsBackUnscoredAndKeepsTheInterrupt() throws Exception {
		CountDownLatch released = new CountDownLatch(1);
		DecisionModel slow = prompt -> {
			try {
				released.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return yes(prompt, 0.9d);
		};
		DecisionDocumentReranker reranker = DecisionDocumentReranker.builder(slow).build();

		Thread caller = Thread.currentThread();
		Thread interrupter = new Thread(() -> {
			try {
				Thread.sleep(200);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			caller.interrupt();
		});
		interrupter.start();
		try {
			List<Document> reranked = reranker.process(this.query, documents(1, 7));

			assertThat(reranked).extracting(Document::getId).containsExactly("doc-1", "doc-7");
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
			released.countDown();
			interrupter.join();
		}
	}

	@Test
	void fansTheCallsOutOverTheConcurrencyLimit() throws Exception {
		// Three documents, concurrency three: all three calls must be in flight together,
		// or the barrier never opens and the test times out instead of hanging.
		CyclicBarrier allInFlight = new CyclicBarrier(3);
		DecisionModel waiting = prompt -> {
			try {
				allInFlight.await(5, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			return yes(prompt, indexOf(prompt) / 10.0d);
		};

		List<Document> reranked = DecisionDocumentReranker.builder(waiting)
			.concurrency(3)
			.build()
			.process(this.query, documents(1, 7, 9));

		assertThat(reranked).extracting(Document::getId).containsExactly("doc-9", "doc-7", "doc-1");
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_21)
	void runsTheCallsOnVirtualThreadsWhenAsked() {
		List<Boolean> virtual = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			virtual.add(isVirtual(Thread.currentThread()));
			return yes(prompt, 0.5d);
		};

		DecisionDocumentReranker.builder(recording).virtualThreads(true).build().process(this.query, documents(1, 7));

		assertThat(virtual).containsExactly(true, true);
	}

	@Test
	@DisabledForJreRange(min = JRE.JAVA_21)
	void virtualThreadsFailFastOnAnOlderJdk() {
		DecisionDocumentReranker.Builder builder = DecisionDocumentReranker.builder(prompt -> yes(prompt, 0.5d))
			.virtualThreads(true);

		assertThatThrownBy(builder::build).isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("JDK");
	}

	@Test
	void anEmptyResultSetIsNotACall() {
		DecisionModel never = prompt -> {
			throw new AssertionError("must not be called");
		};

		assertThat(DecisionDocumentReranker.builder(never).build().process(this.query, List.of())).isEmpty();
	}

	@Test
	void rejectsInvalidBuilderArguments() {
		DecisionModel model = prompt -> yes(prompt, 0.5d);

		assertThatThrownBy(() -> DecisionDocumentReranker.builder(model).topK(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DecisionDocumentReranker.builder(model).minimumScore(1.5d))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DecisionDocumentReranker.builder(model).concurrency(0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static DecisionModel scoring(DoubleFunction<Double> scoreForIndex) {
		return prompt -> yes(prompt, scoreForIndex.apply(indexOf(prompt)));
	}

	private static DecisionModel failingFor(int failingIndex, DoubleFunction<Double> scoreForIndex) {
		return prompt -> {
			int index = indexOf(prompt);
			if (index == failingIndex) {
				throw new IllegalStateException("boom for doc-" + index);
			}
			return yes(prompt, scoreForIndex.apply(index));
		};
	}

	private static DecisionResponse yes(DecisionPrompt prompt, double probability) {
		return DecisionResponse.builder(prompt)
			.answer(DecisionDocumentReranker.QUESTION_NAME, YesNoAnswer.builder().probability(probability).build())
			.build();
	}

	private static int indexOf(DecisionPrompt prompt) {
		FieldsInput input = (FieldsInput) prompt.getInstructions();
		String passage = String.valueOf(input.getFields().get(DecisionDocumentReranker.PASSAGE_FIELD));
		return Integer.parseInt(passage.substring(passage.lastIndexOf('-') + 1));
	}

	/**
	 * {@code Thread.isVirtual()} only exists from JDK 21, which the sources cannot
	 * assume.
	 */
	private static boolean isVirtual(Thread thread) {
		try {
			return (boolean) Thread.class.getMethod("isVirtual").invoke(thread);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Document mediaOnly(String id) {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("http://image")).build();
		return Document.builder().id(id).media(media).build();
	}

	private static List<Document> documents(int... ids) {
		return Arrays.stream(ids)
			.mapToObj(id -> Document.builder().id("doc-" + id).text("passage doc-" + id).build())
			.toList();
	}

}
