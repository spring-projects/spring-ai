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
 * @author Christian Tzolov
 */
class DecisionQuestionTests {

	@Test
	void questionKindsAreSealed() {
		assertThat(DecisionQuestion.class.isSealed()).isTrue();
		assertThat(DecisionQuestion.class.getPermittedSubclasses()).containsExactly(YesNoQuestion.class,
				ChoiceQuestion.class, ScoreQuestion.class);
	}

	@Test
	void yesNoQuestion() {
		var question = YesNoQuestion.builder()
			.text("Does the customer report a defect?")
			.whenYes("Part of the item does not work as it should")
			.whenNo("The item works, and the message is about something else")
			.build();

		assertThat(question.getText()).isEqualTo("Does the customer report a defect?");
		assertThat(question.getWhenYes()).isEqualTo("Part of the item does not work as it should");
		assertThat(question.getWhenNo()).isEqualTo("The item works, and the message is about something else");
		assertThat(YesNoQuestion.of("Does the customer report a defect?").getWhenYes()).isNull();
	}

	@Test
	void yesNoQuestionRequiresText() {
		assertThatThrownBy(() -> YesNoQuestion.of("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text");
		assertThatThrownBy(() -> YesNoQuestion.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("text");
	}

	@Test
	void blankCriteriaAndDescriptionsCountAsNone() {
		var yesNo = YesNoQuestion.builder()
			.text("Does the customer report a defect?")
			.whenYes("")
			.whenNo(" \t")
			.build();
		assertThat(yesNo.getWhenYes()).isNull();
		assertThat(yesNo.getWhenNo()).isNull();
		assertThat(yesNo).isEqualTo(YesNoQuestion.of("Does the customer report a defect?"));

		var choice = ChoiceQuestion.builder()
			.text("What is the message about?")
			.option("delivery", "")
			.option("account", " ")
			.build();
		assertThat(choice.getOptions()).containsEntry("delivery", null).containsEntry("account", null);

		assertThat(ScoreLevel.builder().label("unhappy").description(" ").build().getDescription()).isNull();
		assertThat(ScoreQuestion.builder()
			.text("How satisfied is the customer?")
			.level("unhappy", "")
			.level("happy")
			.build()
			.getLevels()).extracting(ScoreLevel::getDescription).containsOnlyNulls();
	}

	@Test
	void choiceQuestionKeepsOptionOrder() {
		var question = ChoiceQuestion.builder()
			.text("What is the message about?")
			.option("delivery", "Where an order is, or when it will arrive")
			.option("product")
			.option("account", "Login, address or payment details")
			.build();

		assertThat(question.getOptionNames()).containsExactly("delivery", "product", "account");
		assertThat(question.getOptions()).containsEntry("delivery", "Where an order is, or when it will arrive")
			.containsEntry("product", null);
		assertThat(ChoiceQuestion.of("What is the message about?", "a", "b").getOptionNames()).containsExactly("a",
				"b");

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
			.text("How satisfied is the customer?")
			.level("unhappy")
			.level("neutral", "The customer is neither happy nor unhappy")
			.level("happy")
			.build();

		assertThat(question.getLevelLabels()).containsExactly("unhappy", "neutral", "happy");
		assertThat(question.getLevels().get(1).getDescription()).isEqualTo("The customer is neither happy nor unhappy");
		assertThat(ScoreQuestion.of("How satisfied is the customer?", "unhappy", "happy").getLevels()).hasSize(2);
	}

	@Test
	void scoreQuestionNeedsTwoLevels() {
		assertThatThrownBy(() -> ScoreQuestion.of("How satisfied is the customer?", "only"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 2 levels");
		assertThatThrownBy(() -> ScoreLevel.builder().label("").build()).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ScoreLevel.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
