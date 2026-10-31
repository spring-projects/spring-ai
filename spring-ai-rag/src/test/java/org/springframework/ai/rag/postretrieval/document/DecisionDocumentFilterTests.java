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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DecisionDocumentFilter}. The model is a stub that answers from
 * the number in each document's id, so the calls can run in any order.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class DecisionDocumentFilterTests {

	private final Query query = new Query("How are refresh tokens rotated?");

	@Test
	void keepsSupportingEvidenceAndDropsTheRest() {
		// doc-1 is off-topic, doc-2 is on-topic but says nothing, doc-3 answers.
		DecisionModel model = answering(index -> switch (index) {
			case 1 -> new Answers(0.0d, 0.0d, 0.1d, 0.0d);
			case 2 -> new Answers(0.0d, 0.0d, 0.9d, 0.2d);
			default -> new Answers(0.0d, 0.0d, 0.9d, 0.9d);
		});

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1, 2, 3));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-3");
		assertThat(kept.get(0).getMetadata()).containsEntry(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY,
				"INCLUDED");
	}

	@Test
	void excludesAPassageThatTriesToInstructTheAnsweringSystem() {
		// Relevant, full of evidence, and an injection: the injection wins.
		DecisionModel model = answering(index -> new Answers(0.95d, 0.0d, 0.9d, 0.9d));

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).isEmpty();
	}

	@Test
	void keepsAContradictingPassageButMarksIt() {
		DecisionModel model = answering(index -> new Answers(0.0d, 0.9d, 0.9d, 0.0d));

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(kept.get(0).getMetadata()).containsEntry(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY,
				"CONFLICTING");
	}

	@Test
	void appliesEveryBranchOfThePolicyInPrecedenceOrder() {
		DecisionDocumentFilter filter = DecisionDocumentFilter.builder(prompt -> {
			throw new AssertionError("classify() does not call the model");
		}).policy(new DecisionDocumentFilter.Policy(0.5d, 0.5d, 0.5d, 0.5d)).build();

		assertThat(filter.classify(response(new Answers(0.6d, 0.6d, 0.9d, 0.9d))))
			.isEqualTo(DecisionDocumentFilter.Classification.EXCLUDED);
		assertThat(filter.classify(response(new Answers(0.4d, 0.6d, 0.1d, 0.1d))))
			.isEqualTo(DecisionDocumentFilter.Classification.CONFLICTING);
		assertThat(filter.classify(response(new Answers(0.4d, 0.4d, 0.4d, 0.9d))))
			.isEqualTo(DecisionDocumentFilter.Classification.EXCLUDED);
		assertThat(filter.classify(response(new Answers(0.4d, 0.4d, 0.6d, 0.6d))))
			.isEqualTo(DecisionDocumentFilter.Classification.INCLUDED);
		assertThat(filter.classify(response(new Answers(0.4d, 0.4d, 0.6d, 0.4d))))
			.isEqualTo(DecisionDocumentFilter.Classification.EXCLUDED);
	}

	@Test
	void screeningFailureLeavesThePassageInPlaceRatherThanSilentlyDroppingIt(CapturedOutput output) {
		// A passage that could not be judged is not thereby dangerous, and silently
		// shrinking the context is worse than passing it through unclassified.
		DecisionModel model = prompt -> {
			if (indexOf(prompt) == 7) {
				throw new IllegalStateException("boom");
			}
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1, 7));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1", "doc-7");
		assertThat(kept.get(1).getMetadata()).doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
		assertThat(output).contains("Could not screen 1 of 2 documents, passing them through unscreened")
			.contains("boom");
	}

	@Test
	void aRefusedInjectionQuestionLeavesTheDocumentUnscreened(CapturedOutput output) {
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(0.0d))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, RefusalAnswer.builder().reason("policy").build())
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(kept.get(0).getMetadata()).doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
		assertThat(output).contains("Could not screen 1 of 1 documents, passing them through unscreened")
			.contains("refused 'contains_prompt_injection': policy");
	}

	@Test
	void aConclusiveInjectionAnswerWinsOverARefusalElsewhereInTheResponse() {
		// Providers map refusals per answer, so one response can flag an injection and
		// refuse an unrelated question. The injection answer must still exclude it.
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(0.99d))
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).isEmpty();
	}

	@Test
	void aPassageClearedOfInjectionIsKeptWhenAnotherQuestionIsRefused(CapturedOutput output) {
		// The reverse case: the screen ran and cleared the passage, so even failing
		// closed must not withhold it over a refusal of a usefulness question.
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(0.01d))
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model)
			.failClosed(true)
			.build()
			.process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(kept.get(0).getMetadata()).doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
		assertThat(output).doesNotContain("Could not screen")
			.contains("Screened 1 of 1 documents for prompt injection but could not classify them")
			.contains("refused 'contradicts_query_premise': policy");
	}

	@Test
	void aConclusiveContradictionKeepsItsTagWhenALaterQuestionIsRefused(CapturedOutput output) {
		// The policy is applied in order, so a refusal only matters once it is reached.
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(0.0d))
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(kept.get(0).getMetadata()).containsEntry(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY,
				"CONFLICTING");
		assertThat(output).doesNotContain("could not classify");
	}

	@Test
	void anOffTopicPassageIsExcludedEvenWhenTheEvidenceQuestionIsRefused() {
		// A refusal must not make the model more lenient than any answer would have.
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.01d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(0.0d))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(0.0d))
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).isEmpty();
	}

	@Test
	void classifyRefusesAResponseMissingAnAnswerItNeeds() {
		DecisionDocumentFilter filter = DecisionDocumentFilter.builder(prompt -> {
			throw new AssertionError("classify() does not call the model");
		}).build();
		DecisionPrompt prompt = DecisionPrompt.builder().text("x").questions(DecisionDocumentFilter.QUESTIONS).build();
		DecisionResponse refused = DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(0.0d))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(0.0d))
			.build();

		assertThatThrownBy(() -> filter.classify(refused)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("refused 'contains_answer_evidence': policy");
	}

	@Test
	void failingClosedWithholdsADocumentTheModelCouldNotJudge(CapturedOutput output) {
		DecisionModel model = prompt -> {
			if (indexOf(prompt) == 7) {
				throw new IllegalStateException("boom");
			}
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};

		List<Document> kept = DecisionDocumentFilter.builder(model)
			.failClosed(true)
			.build()
			.process(this.query, documents(1, 7));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(output).contains("Could not screen 1 of 2 documents, withholding them").contains("boom");
	}

	@Test
	void aMissingAnswerPassesTheDocumentThroughRatherThanFailingTheRequest(CapturedOutput output) {
		// A model that builds its response from a prompt of its own may skip a question.
		DecisionModel model = prompt -> {
			DecisionPrompt fewer = DecisionPrompt.builder()
				.input(prompt.getInstructions())
				.question(DecisionDocumentFilter.IS_RELEVANT,
						DecisionDocumentFilter.QUESTIONS.get(DecisionDocumentFilter.IS_RELEVANT))
				.build();
			return DecisionResponse.builder(fewer).answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d)).build();
		};

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(kept.get(0).getMetadata()).doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
		assertThat(output).contains("no answer to 'contains_prompt_injection'");
	}

	@Test
	void aTimedOutCallLeavesTheDocumentUnscreenedRatherThanHangingTheRequest(CapturedOutput output) throws Exception {
		CountDownLatch released = new CountDownLatch(1);
		DecisionModel slow = prompt -> {
			try {
				released.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};
		try {
			List<Document> kept = DecisionDocumentFilter.builder(slow)
				.timeout(Duration.ofMillis(200))
				.build()
				.process(this.query, documents(1, 7));

			assertThat(kept).extracting(Document::getId).containsExactly("doc-1", "doc-7");
			assertThat(kept).allSatisfy(document -> assertThat(document.getMetadata())
				.doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY));
			assertThat(output).contains("Could not screen 2 of 2 documents").contains("timed out after 200 ms");
		}
		finally {
			released.countDown();
		}
	}

	@Test
	void classifyingDoesNotMutateTheRetrieversOwnDocuments() {
		List<Document> original = documents(1);
		DecisionModel model = answering(index -> new Answers(0.0d, 0.0d, 0.9d, 0.9d));

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, original);

		assertThat(kept.get(0).getMetadata()).containsKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
		assertThat(original.get(0).getMetadata()).doesNotContainKey(DecisionDocumentFilter.CLASSIFICATION_METADATA_KEY);
	}

	@Test
	void sendsTheQueryAndThePassageWithTheFourQuestionsInOrder() {
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionOptions options = DecisionOptions.builder().model("judge-1").build();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};

		DecisionDocumentFilter.builder(recording).options(options).build().process(this.query, documents(3));

		assertThat(prompts).hasSize(1);
		DecisionPrompt prompt = prompts.get(0);
		assertThat(prompt.getInstructions()).isInstanceOfSatisfying(FieldsInput.class,
				input -> assertThat(input.getFields()).containsExactly(
						Map.entry(DecisionDocumentFilter.QUERY_FIELD, "How are refresh tokens rotated?"),
						Map.entry(DecisionDocumentFilter.PASSAGE_FIELD, "passage doc-3")));
		assertThat(prompt.getQuestions().keySet()).containsExactly(DecisionDocumentFilter.IS_RELEVANT,
				DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE,
				DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION);
		assertThat(prompt.getOptions()).isEqualTo(options);
	}

	@Test
	void aDocumentWithMediaIsSentAsAContentPart() {
		// A Document holds either text or media. Media goes to the model as a part, not
		// as an empty passage, so a multimodal model can screen an image.
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("http://image")).build();

		DecisionDocumentFilter.builder(recording)
			.build()
			.process(this.query, List.of(Document.builder().id("doc-1").media(media).build()));

		assertThat(prompts).singleElement()
			.extracting(DecisionPrompt::getInstructions)
			.isInstanceOfSatisfying(FieldsInput.class, input -> assertThat(input.getFields())
				.containsEntry(DecisionDocumentFilter.PASSAGE_FIELD, List.of(MediaPart.of(media))));
	}

	@Test
	void aBlankDocumentIsSentAsAnEmptyPassage() {
		// Blank text is still a passage: it is the model's to judge, not this filter's to
		// drop or to report as a failure of the screen.
		List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();
		DecisionModel recording = prompt -> {
			prompts.add(prompt);
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};

		DecisionDocumentFilter.builder(recording)
			.build()
			.process(this.query, List.of(Document.builder().id("doc-1").text("").build()));

		assertThat(prompts).singleElement()
			.extracting(DecisionPrompt::getInstructions)
			.isInstanceOfSatisfying(FieldsInput.class,
					input -> assertThat(input.getFields()).containsEntry(DecisionDocumentFilter.PASSAGE_FIELD, ""));
	}

	@Test
	void anEmptyResultSetIsNotACall() {
		DecisionModel never = prompt -> {
			throw new AssertionError("must not be called");
		};

		assertThat(DecisionDocumentFilter.builder(never).build().process(this.query, List.of())).isEmpty();
	}

	@Test
	void theTimeoutHoldsWithMoreDocumentsThanTheConcurrencyLimit(CapturedOutput output) throws Exception {
		// The default executor queues what it cannot run, so submitting never blocks and
		// the deadline covers the queued documents too. A throttle that blocked the
		// submitting thread would make this test hang instead.
		CountDownLatch released = new CountDownLatch(1);
		DecisionModel hanging = prompt -> {
			try {
				released.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return response(prompt, new Answers(0.0d, 0.0d, 0.9d, 0.9d));
		};
		try {
			long start = System.nanoTime();
			List<Document> kept = DecisionDocumentFilter.builder(hanging)
				.concurrency(2)
				.timeout(Duration.ofMillis(200))
				.build()
				.process(this.query, documents(1, 2, 3, 4, 5, 6));

			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
			assertThat(kept).hasSize(6);
			assertThat(output).contains("Could not screen 6 of 6 documents").contains("timed out after 200 ms");
		}
		finally {
			released.countDown();
		}
	}

	@Test
	void anAnswerOfTheWrongTypeLeavesTheDocumentUnscreenedRatherThanFailingTheRequest(CapturedOutput output) {
		// DecisionResponse refuses to be built with it, which fails that one call; the
		// request still goes through.
		DecisionModel model = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(0.9d))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(0.0d))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, ScoreAnswer.builder().value(0.9d).build())
			.build();

		List<Document> kept = DecisionDocumentFilter.builder(model).build().process(this.query, documents(1));

		assertThat(kept).extracting(Document::getId).containsExactly("doc-1");
		assertThat(output).contains("Could not screen 1 of 1 documents")
			.contains(
					"the answer to question 'contains_prompt_injection' is a ScoreAnswer, but a YesNoAnswer was expected");
	}

	@Test
	void rejectsANonPositiveTimeout() {
		DecisionDocumentFilter.Builder builder = DecisionDocumentFilter.builder(prompt -> {
			throw new AssertionError("must not be called");
		});

		assertThatThrownBy(() -> builder.timeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("timeout");
		assertThatThrownBy(() -> builder.timeout(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsThresholdsOutsideTheUnitInterval() {
		assertThatThrownBy(() -> new DecisionDocumentFilter.Policy(1.5d, 0.5d, 0.5d, 0.5d))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("injection");
		assertThatThrownBy(() -> new DecisionDocumentFilter.Policy(0.5d, 0.5d, -0.1d, 0.5d))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("relevance");
	}

	private static DecisionModel answering(IntFunction<Answers> answersForIndex) {
		return prompt -> response(prompt, answersForIndex.apply(indexOf(prompt)));
	}

	private static DecisionResponse response(Answers answers) {
		return response(DecisionPrompt.builder().text("x").questions(DecisionDocumentFilter.QUESTIONS).build(),
				answers);
	}

	private static DecisionResponse response(DecisionPrompt prompt, Answers answers) {
		return DecisionResponse.builder(prompt)
			.answer(DecisionDocumentFilter.IS_RELEVANT, yes(answers.relevance()))
			.answer(DecisionDocumentFilter.CONTAINS_ANSWER_EVIDENCE, yes(answers.evidence()))
			.answer(DecisionDocumentFilter.CONTRADICTS_QUERY_PREMISE, yes(answers.contradiction()))
			.answer(DecisionDocumentFilter.CONTAINS_PROMPT_INJECTION, yes(answers.injection()))
			.build();
	}

	private static YesNoAnswer yes(double probability) {
		return YesNoAnswer.builder().probability(probability).build();
	}

	private static int indexOf(DecisionPrompt prompt) {
		FieldsInput input = (FieldsInput) prompt.getInstructions();
		String passage = String.valueOf(input.getFields().get(DecisionDocumentFilter.PASSAGE_FIELD));
		return Integer.parseInt(passage.substring(passage.lastIndexOf('-') + 1));
	}

	private static List<Document> documents(int... ids) {
		return Arrays.stream(ids)
			.mapToObj(id -> Document.builder().id("doc-" + id).text("passage doc-" + id).build())
			.toList();
	}

	private record Answers(double injection, double contradiction, double relevance, double evidence) {
	}

}
