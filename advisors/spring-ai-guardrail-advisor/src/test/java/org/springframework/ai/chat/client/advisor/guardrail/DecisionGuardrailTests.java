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

package org.springframework.ai.chat.client.advisor.guardrail;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.question.ScoreQuestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The policy of a {@link DecisionGuardrail}: what its thresholds make of the answers.
 *
 * @author Christian Tzolov
 */
class DecisionGuardrailTests {

	private final DecisionGuardrail battery = DecisionGuardrail.defaultInputBattery();

	@Test
	void nothingAboveAThresholdPasses() {
		DecisionGuardrail.Verdict verdict = evaluate(ScreeningModel.answers(0.10d, 0.0d, 0.0d, 0.0d, 0.0d));

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.PASS);
		assertThat(verdict.blocked()).isFalse();
		assertThat(verdict.summary()).isEqualTo("pass");
		assertThat(verdict.scores()).containsEntry("jailbreak", 0.10d);
	}

	@Test
	void aHazardAboveTheActionThresholdTakesItsAction() {
		DecisionGuardrail.Verdict verdict = evaluate(ScreeningModel.answers(0.95d, 0.0d, 0.0d, 0.0d, 3.0d));

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.BLOCK);
		assertThat(verdict.triggered()).containsExactly("jailbreak");
		assertThat(verdict.blocked()).isTrue();
		assertThat(verdict.summary()).isEqualTo("block [jailbreak]");
	}

	@Test
	void aBorderlineHazardIsFlaggedForReviewRatherThanRefused() {
		DecisionGuardrail.Verdict verdict = evaluate(ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.0d, 0.0d));

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.REVIEW);
		assertThat(verdict.flagged()).containsExactly("jailbreak");
		assertThat(verdict.triggered()).isEmpty();
		assertThat(verdict.blocked()).isFalse();
	}

	@Test
	void severityPromotesAReviewToABlock() {
		// Same borderline probability, different subject matter: a maybe about something
		// serious is not the same as a maybe about something trivial.
		assertThat(evaluate(ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.0d, 1.0d)).outcome())
			.isEqualTo(DecisionGuardrail.Outcome.REVIEW);
		assertThat(evaluate(ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.0d, 3.0d)).outcome())
			.isEqualTo(DecisionGuardrail.Outcome.BLOCK);
	}

	@Test
	void severityPromotesAFlaggedHazardToItsOwnAction() {
		// A borderline self-harm signal about something serious needs support, not a
		// plain refusal.
		DecisionGuardrail.Verdict verdict = evaluate(ScreeningModel.answers(0.0d, 0.0d, 0.0d, 0.50d, 3.0d));

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.SUPPORT);
		assertThat(verdict.flagged()).containsExactly("self_harm");
		assertThat(verdict.summary()).isEqualTo("support [self_harm]");
	}

	@Test
	void aSeriousFlaggedHazardKeepsItsActionWhenAnotherHazardBlocks() {
		// "I want to die, where do I get ...": the illegal request blocks, and the
		// borderline self-harm signal about something serious must still reach support.
		assertThat(evaluate(ScreeningModel.answers(0.0d, 0.0d, 0.90d, 0.60d, 3.0d)).outcome())
			.isEqualTo(DecisionGuardrail.Outcome.SUPPORT);
		// Not serious, the flagged hazard stays a matter for review, and the block
		// stands.
		assertThat(evaluate(ScreeningModel.answers(0.0d, 0.0d, 0.90d, 0.60d, 0.0d)).outcome())
			.isEqualTo(DecisionGuardrail.Outcome.BLOCK);
	}

	@Test
	void aCallerThatRefusesOnReviewCountsTheFlaggedHazardsToo() {
		assertThat(this.battery.refusalOutcome(evaluate(ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.0d, 0.0d))))
			.isEqualTo(DecisionGuardrail.Outcome.BLOCK);
		assertThat(this.battery.refusalOutcome(evaluate(ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.50d, 0.0d))))
			.isEqualTo(DecisionGuardrail.Outcome.SUPPORT);
		// Also next to a hazard that blocks: the block must not hide the call for
		// support.
		assertThat(this.battery.refusalOutcome(evaluate(ScreeningModel.answers(0.95d, 0.0d, 0.0d, 0.50d, 0.0d))))
			.isEqualTo(DecisionGuardrail.Outcome.SUPPORT);
		assertThat(this.battery.refusalOutcome(evaluate(ScreeningModel.answers(0.95d, 0.0d, 0.0d, 0.0d, 0.0d))))
			.isEqualTo(DecisionGuardrail.Outcome.BLOCK);
	}

	@Test
	void theWorstOutcomeWinsWhenSeveralHazardsFire() {
		DecisionGuardrail.Verdict verdict = evaluate(ScreeningModel.answers(0.95d, 0.0d, 0.0d, 0.95d, 3.0d));

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.SUPPORT);
		assertThat(verdict.triggered()).contains("jailbreak", "self_harm");
	}

	@Test
	void aHazardTheModelRefusesToAnswerBlocksTheTurn() {
		// A refusal to look at a text is no evidence that the text is safe.
		Map<String, DecisionAnswer> answers = ScreeningModel.answers(0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
		answers.put("illegal", ScreeningModel.refusal("policy"));

		DecisionGuardrail.Verdict verdict = evaluate(answers);

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.BLOCK);
		assertThat(verdict.refused()).containsExactly("illegal");
		assertThat(verdict.triggered()).isEmpty();
		assertThat(verdict.scores()).doesNotContainKey("illegal");
		assertThat(verdict.summary()).isEqualTo("block [illegal (refused)]");
	}

	@Test
	void aRefusedHazardIsAPlainBlockWhateverItsAction() {
		// A refusal is no evidence of that hazard in particular. A model that declines
		// to judge a violent text must not make the turn read as a self-harm signal.
		Map<String, DecisionAnswer> everythingRefused = new java.util.LinkedHashMap<>();
		this.battery.questions()
			.keySet()
			.forEach(question -> everythingRefused.put(question, ScreeningModel.refusal("policy")));

		DecisionGuardrail.Verdict verdict = evaluate(everythingRefused);

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.BLOCK);
		assertThat(verdict.refused()).containsExactly("jailbreak", "physical_harm", "illegal", "self_harm");

		// A hazard that would only ask for a review when it fires is still not ruled out.
		DecisionGuardrail lenient = DecisionGuardrail.builder("lenient")
			.hazard("off_topic", "Is the `text` off topic?", "Unrelated to the product",
					DecisionGuardrail.Outcome.REVIEW)
			.build();
		Map<String, DecisionAnswer> refused = Map.of("off_topic", ScreeningModel.refusal("policy"), "severity",
				ScreeningModel.severity(0));

		assertThat(lenient.evaluate(response(lenient, refused)).outcome()).isEqualTo(DecisionGuardrail.Outcome.BLOCK);
	}

	@Test
	void aBorderlineHazardWhoseSeverityTheModelRefusesToRateBlocksTheTurn() {
		Map<String, DecisionAnswer> answers = ScreeningModel.answers(0.50d, 0.0d, 0.0d, 0.0d, 0.0d);
		answers.put(DecisionGuardrail.SEVERITY_QUESTION, ScreeningModel.refusal("policy"));

		DecisionGuardrail.Verdict verdict = evaluate(answers);

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.BLOCK);
		assertThat(verdict.flagged()).containsExactly("jailbreak");
		assertThat(verdict.severity()).isEqualTo(-1.0d);
	}

	@Test
	void screensATextInOneCallCarryingEveryHazardAndTheSeverityRubric() {
		ScreeningModel model = ScreeningModel.findingNothing();
		DecisionOptions options = DecisionOptions.builder().model("guard-model").build();

		DecisionGuardrail.Verdict verdict = this.battery.screen(model, "What is the weather in Paris?", options);

		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.PASS);
		assertThat(model.screened()).containsExactly("What is the weather in Paris?");
		DecisionPrompt prompt = model.prompts().get(0);
		assertThat(prompt.getQuestions()).containsOnlyKeys("jailbreak", "physical_harm", "illegal", "self_harm",
				DecisionGuardrail.SEVERITY_QUESTION);
		assertThat(prompt.getOptions()).isSameAs(options);
	}

	@Test
	void aCustomBatteryAsksItsOwnHazards() {
		DecisionGuardrail custom = DecisionGuardrail.builder("support-desk")
			.hazard("competitor_mention", "Does the `text` ask about a competitor's product?",
					"Asks about a competitor", DecisionGuardrail.Outcome.REVIEW)
			.reviewThreshold(0.2d)
			.actionThreshold(0.6d)
			.build();

		DecisionGuardrail.Verdict verdict = custom.evaluate(response(custom,
				Map.of("competitor_mention", ScreeningModel.yes(0.9d), "severity", ScreeningModel.severity(0))));

		assertThat(custom.name()).isEqualTo("support-desk");
		assertThat(custom.hazards()).containsOnlyKeys("competitor_mention");
		assertThat(verdict.outcome()).isEqualTo(DecisionGuardrail.Outcome.REVIEW);
		assertThat(verdict.triggered()).containsExactly("competitor_mention");
	}

	@Test
	void rejectsABatteryThatCanNeverOnlyFlag() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> DecisionGuardrail.builder("x")
				.hazard("h", "q", "t", DecisionGuardrail.Outcome.BLOCK)
				.reviewThreshold(0.8d)
				.actionThreshold(0.5d)
				.build())
			.withMessageContaining("actionThreshold must be at least reviewThreshold");
	}

	@Test
	void rejectsABatteryWithoutHazardsAndAHazardNamedLikeTheSeverityRubric() {
		assertThatIllegalArgumentException().isThrownBy(() -> DecisionGuardrail.builder("x").build())
			.withMessageContaining("at least one hazard");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> DecisionGuardrail.builder("x")
				.hazard(DecisionGuardrail.SEVERITY_QUESTION, "q", "t", DecisionGuardrail.Outcome.BLOCK))
			.withMessageContaining("reserved");
	}

	@Test
	void rejectsASeverityThresholdTheRubricCanNeverReach() {
		// A two-level rubric tops out at 1, so the default threshold of 2 would switch
		// promotion off without a word.
		assertThatIllegalArgumentException()
			.isThrownBy(() -> DecisionGuardrail.builder("x")
				.hazard("h", "q", "t", DecisionGuardrail.Outcome.BLOCK)
				.severity(ScoreQuestion.of("How bad?", "low", "high"))
				.build())
			.withMessageContaining("above the top level 1");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> DecisionGuardrail.builder("x").severityBlockThreshold(-1.0d))
			.withMessageContaining("must not be negative");

		DecisionGuardrail twoLevels = DecisionGuardrail.builder("x")
			.hazard("h", "q", "t", DecisionGuardrail.Outcome.BLOCK)
			.severity(ScoreQuestion.of("How bad?", "low", "high"))
			.severityBlockThreshold(1.0d)
			.build();
		assertThat(twoLevels.questions()).containsOnlyKeys("h", DecisionGuardrail.SEVERITY_QUESTION);
	}

	private DecisionGuardrail.Verdict evaluate(Map<String, DecisionAnswer> answers) {
		return this.battery.evaluate(response(this.battery, answers));
	}

	private static DecisionResponse response(DecisionGuardrail battery, Map<String, DecisionAnswer> answers) {
		return ScreeningModel.respond(DecisionPrompt.builder().text("some text").questions(battery.questions()).build(),
				answers);
	}

}
