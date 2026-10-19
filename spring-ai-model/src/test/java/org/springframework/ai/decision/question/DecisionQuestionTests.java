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

package org.springframework.ai.decision.question;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link YesNoQuestion}, {@link ChoiceQuestion} and {@link ScoreQuestion}.
 *
 * @author Thomas Vitale
 */
class DecisionQuestionTests {

	@Test
	void yesNoQuestion() {
		var question = YesNoQuestion.builder()
			.text("Is the message spam?")
			.whenYes("It advertises something")
			.whenNo("It is a genuine request")
			.build();

		assertThat(question.getText()).isEqualTo("Is the message spam?");
		assertThat(question.getWhenYes()).isEqualTo("It advertises something");
		assertThat(question.getWhenNo()).isEqualTo("It is a genuine request");
		assertThat(YesNoQuestion.of("Is the message spam?").getWhenYes()).isNull();
	}

	@Test
	void yesNoQuestionRequiresText() {
		assertThatThrownBy(() -> YesNoQuestion.of("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text");
		assertThatThrownBy(() -> YesNoQuestion.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("text");
	}

	@Test
	void choiceQuestionKeepsOptionOrder() {
		var question = ChoiceQuestion.builder()
			.text("Which team?")
			.option("billing", "Invoices and payments")
			.option("technical")
			.option("sales", "Quotes")
			.build();

		assertThat(question.getOptionNames()).containsExactly("billing", "technical", "sales");
		assertThat(question.getOptions()).containsEntry("billing", "Invoices and payments")
			.containsEntry("technical", null);
		assertThat(ChoiceQuestion.of("Which team?", "a", "b").getOptionNames()).containsExactly("a", "b");

		Map<String, String> options = new LinkedHashMap<>();
		options.put("x", "X");
		options.put("y", "Y");
		assertThat(ChoiceQuestion.builder().text("Which?").options(options).build().getOptions())
			.containsExactlyEntriesOf(options);
	}

	@Test
	void choiceQuestionNeedsTwoOptionsAndUniqueNames() {
		assertThatThrownBy(() -> ChoiceQuestion.of("Which?", "only")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 2 options");
		assertThatThrownBy(() -> ChoiceQuestion.builder().text("Which?").option("a").option("a"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("duplicate option 'a'");
	}

	@Test
	void scoreQuestionIndexesLevels() {
		var question = ScoreQuestion.builder()
			.text("How urgent?")
			.level("low")
			.level("medium", "Needs attention this week")
			.level("high")
			.build();

		assertThat(question.getLevelLabels()).containsExactly("low", "medium", "high");
		assertThat(question.getLevels().get(1).getDescription()).isEqualTo("Needs attention this week");
		assertThat(ScoreQuestion.of("How urgent?", "low", "high").getLevels()).hasSize(2);
	}

	@Test
	void scoreQuestionNeedsTwoLevels() {
		assertThatThrownBy(() -> ScoreQuestion.of("How urgent?", "only")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 2 levels");
		assertThatThrownBy(() -> ScoreLevel.builder().label("").build()).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ScoreLevel.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
