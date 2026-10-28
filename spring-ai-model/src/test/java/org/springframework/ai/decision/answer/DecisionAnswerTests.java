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

package org.springframework.ai.decision.answer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link YesNoAnswer}, {@link ChoiceAnswer}, {@link ScoreAnswer} and
 * {@link RefusalAnswer}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class DecisionAnswerTests {

	@Test
	void answerKindsAreSealed() {
		assertThat(DecisionAnswer.class.isSealed()).isTrue();
		assertThat(DecisionAnswer.class.getPermittedSubclasses()).containsExactly(YesNoAnswer.class, ChoiceAnswer.class,
				ScoreAnswer.class, RefusalAnswer.class);
	}

	@Test
	void yesNoAnswerAppliesThreshold() {
		var answer = YesNoAnswer.builder().probability(0.7).build();

		assertThat(answer.getProbability()).isEqualTo(0.7);
		assertThat(answer.isYes()).isTrue();
		assertThat(answer.isYes(0.9)).isFalse();
		assertThat(YesNoAnswer.builder().probability(0.5).build().isYes()).isTrue();
		assertThat(YesNoAnswer.builder().probability(0.49).build().isYes()).isFalse();
	}

	@Test
	void choiceAnswerRejectsNullProbability() {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put("a", 0.5);
		probabilities.put("b", null);

		assertThatThrownBy(() -> ChoiceAnswer.builder().value("a").probabilities(probabilities).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("'b'");
	}

	@Test
	void probabilitiesMustBeBetweenZeroAndOne() {
		assertThat(YesNoAnswer.builder().probability(0).build().getProbability()).isZero();
		assertThat(YesNoAnswer.builder().probability(1).build().getProbability()).isEqualTo(1.0);
		assertThatThrownBy(() -> YesNoAnswer.builder().probability(1.1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 0 and 1");
		assertThatThrownBy(() -> YesNoAnswer.builder().probability(Double.NaN).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ChoiceAnswer.builder().value("a").probabilities(Map.of("a", 1.5)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("'a'");
		assertThatThrownBy(() -> ChoiceAnswer.builder().value("a").confidence(-0.1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("confidence");
		assertThatThrownBy(() -> ScoreAnswer.builder().value(1).probabilities(List.of(0.5, 2.0)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 0 and 1");
	}

	@Test
	void choiceAnswerExposesProbabilitiesAndConfidence() {
		var answer = ChoiceAnswer.builder()
			.value("technical")
			.probabilities(Map.of("billing", 0.2, "technical", 0.8))
			.confidence(0.9)
			.build();

		assertThat(answer.getValue()).isEqualTo("technical");
		assertThat(answer.getProbability()).isEqualTo(0.8);
		assertThat(answer.getProbability("billing")).isEqualTo(0.2);
		assertThat(answer.getProbability("unknown")).isNull();
		assertThat(answer.getConfidence()).isEqualTo(0.9);
	}

	@Test
	void choiceAnswerWithoutProbabilities() {
		var answer = ChoiceAnswer.builder().value("technical").build();

		assertThat(answer.getProbabilities()).isEmpty();
		assertThat(answer.getProbability()).isNull();
		assertThat(answer.getRankedOptions()).isEmpty();
		assertThat(answer.getConfidence()).isNull();
	}

	@Test
	void choiceAnswerRanksOptionsByProbability() {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put("sales", 0.1);
		probabilities.put("billing", 0.3);
		probabilities.put("technical", 0.6);

		assertThat(ChoiceAnswer.builder().value("technical").probabilities(probabilities).build().getRankedOptions())
			.containsExactly("technical", "billing", "sales");
	}

	@Test
	void choiceAnswerKeepsReportedOrderForTies() {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put("a", 0.4);
		probabilities.put("b", 0.4);
		probabilities.put("c", 0.2);

		assertThat(ChoiceAnswer.builder().value("a").probabilities(probabilities).build().getRankedOptions())
			.containsExactly("a", "b", "c");
	}

	@Test
	void scoreAnswerNearestLevel() {
		var answer = ScoreAnswer.builder().value(1.6).probabilities(List.of(0.1, 0.2, 0.7)).confidence(0.8).build();

		assertThat(answer.getValue()).isEqualTo(1.6);
		assertThat(answer.getNearestLevel()).isEqualTo(2);
		assertThat(answer.getRoundedLevel()).isEqualTo(2);
		assertThat(answer.getProbabilities()).containsExactly(0.1, 0.2, 0.7);
		assertThat(answer.getConfidence()).isEqualTo(0.8);
		assertThatThrownBy(() -> ScoreAnswer.builder().value(-1).build()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void scoreAnswerNearestLevelIsTheMostLikelyOne() {
		var split = ScoreAnswer.builder().value(1.0).probabilities(List.of(0.5, 0.0, 0.5)).build();
		assertThat(split.getNearestLevel()).isZero();
		assertThat(split.getRoundedLevel()).isEqualTo(1);

		var skewed = ScoreAnswer.builder().value(0.7).probabilities(List.of(0.4, 0.5, 0.1)).build();
		assertThat(skewed.getNearestLevel()).isEqualTo(1);
		assertThat(skewed.getRoundedLevel()).isEqualTo(1);

		var tail = ScoreAnswer.builder().value(1.2).probabilities(List.of(0.45, 0.1, 0.3, 0.15)).build();
		assertThat(tail.getNearestLevel()).isZero();
		assertThat(tail.getRoundedLevel()).isEqualTo(1);
	}

	@Test
	void scoreAnswerBreaksTiesTowardsTheExpectedValue() {
		assertThat(ScoreAnswer.builder().value(0.8).probabilities(List.of(0.4, 0.4, 0.2)).build().getNearestLevel())
			.isEqualTo(1);
		assertThat(ScoreAnswer.builder().value(0.3).probabilities(List.of(0.4, 0.4, 0.2)).build().getNearestLevel())
			.isZero();
		assertThat(ScoreAnswer.builder().value(1.0).probabilities(List.of(0.5, 0.0, 0.5)).build().getNearestLevel())
			.isZero();
	}

	@Test
	void scoreAnswerNearestLevelFallsBackToRoundingWithoutProbabilities() {
		assertThat(ScoreAnswer.builder().value(0.4).build().getNearestLevel()).isZero();
		assertThat(ScoreAnswer.builder().value(0.5).build().getNearestLevel()).isEqualTo(1);
		assertThat(ScoreAnswer.builder().value(1.5).build().getRoundedLevel()).isEqualTo(2);
		assertThat(ScoreAnswer.builder().value(1.8).probabilities(List.of(0.0, 0.0, 0.0)).build().getNearestLevel())
			.isEqualTo(2);
	}

	@Test
	void refusalAnswer() {
		assertThat(RefusalAnswer.builder().reason("policy").build().getReason()).isEqualTo("policy");
		assertThat(RefusalAnswer.builder().reason("policy").build())
			.isEqualTo(RefusalAnswer.builder().reason("policy").build());
	}

}
