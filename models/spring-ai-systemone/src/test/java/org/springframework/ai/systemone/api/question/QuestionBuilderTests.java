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

package org.springframework.ai.systemone.api.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.ai.systemone.api.SystemOneModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The builders reject a malformed question locally rather than letting the API answer 422
 * for it.
 *
 * @author Christian Tzolov
 */
class QuestionBuilderTests {

	@Test
	void choiceKeepsOptionOrder() {
		Choice choice = Choice.builder()
			.instructions("Which team should handle this?")
			.option("billing", "Payments, invoicing, refunds")
			.option("technical", "Bugs, outages, integrations")
			.option("sales", "Pricing, upgrades, new accounts")
			.build();

		assertThat(choice.criteria().keySet()).containsExactly("billing", "technical", "sales");
		assertThat(choice.type()).isEqualTo(QuestionType.CHOICE);
		assertThat(choice.typeName()).isEqualTo("choice");
	}

	@Test
	void choiceAllowsUndescribedOptions() {
		Choice choice = Choice.of("Which option is the value of `field` in `source_text`?", "Beaver Logistics",
				"Dam Logistics", "Beaver Dam Logistics");

		assertThat(choice.criteria()).containsOnlyKeys("Beaver Logistics", "Dam Logistics", "Beaver Dam Logistics");
		assertThat(choice.criteria().get("Dam Logistics")).isEqualTo(JsonContent.NULL);
	}

	@Test
	void choiceRejectsAnEmptyOptionSet() {
		assertThatIllegalArgumentException().isThrownBy(() -> Choice.builder().instructions("Which team?").build())
			.withMessageContaining("at least one option");
	}

	@Test
	void choiceRejectsABlankLabel() {
		assertThatIllegalArgumentException().isThrownBy(() -> Choice.builder().option("  ", "nothing"))
			.withMessageContaining("option label");
	}

	@Test
	void choiceBuildsWithoutInstructions() {
		// Confirmed against the live API: a choice with criteria only is accepted.
		Choice choice = Choice.builder().option("billing").build();

		assertThat(choice.instructions()).isNull();
		assertThat(choice.criteria()).containsOnlyKeys("billing");
	}

	@Test
	void scoreRejectsFewerThanTwoLevels() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Score.builder().instructions("How frustrated?").level("Calm").build())
			.withMessageContaining("at least two levels");
	}

	@Test
	void scoreBuildsWithoutInstructions() {
		// Confirmed against the live API: a score with criteria only is accepted.
		Score score = Score.builder().level("Calm").level("Frustrated").build();

		assertThat(score.instructions()).isNull();
		assertThat(score.maxLevel()).isEqualTo(1);
	}

	@Test
	void scoreExposesItsRubric() {
		Score score = Score.of("How frustrated is the customer?", "Calm", "Frustrated", "Very angry");

		assertThat(score.maxLevel()).isEqualTo(2);
		assertThat(score.levelAt(2)).isEqualTo(JsonContent.of("Very angry"));
		assertThat(score.levelAt(3)).isNull();
		assertThat(score.levelAt(-1)).isNull();
	}

	@Test
	void noulCriteriaAreOmittedWhenNeitherSideIsDescribed() {
		assertThat(Noul.of("Does this convey urgency?").criteria()).isNull();
		assertThat(Noul.builder()
			.instructions("Does this convey urgency?")
			.whenFalse("No urgency expressed")
			.build()
			.criteria()).isEqualTo(new NoulCriteria(null, JsonContent.of("No urgency expressed")));
	}

	@Test
	void everyFieldAcceptsStructure() {
		Noul noul = Noul.builder()
			.instructions(Map.of("question", "Does the `message` ask for a credential?", "inspect", "message"))
			.whenTrue(Map.of("what", "Asks the recipient to reply with a password", "examples",
					List.of("Reply with your password")))
			.whenFalse(Map.of("what", "No sensitive credential is requested"))
			.build();

		assertThat(noul.instructions()).isNotNull();
		assertThat(noul.instructions().asMap()).containsKey("inspect");
		assertThat(noul.criteria()).isNotNull();
		assertThat(noul.criteria().whenTrue()).isNotNull();
		assertThat(noul.criteria().whenTrue().asMap()).containsKey("examples");
	}

	@Test
	void noulBuildsWithCriteriaOnly() {
		// Confirmed against the live API: a noul with criteria only is accepted.
		Noul noul = Noul.builder().whenTrue("Explicitly time-sensitive").whenFalse("No urgency expressed").build();

		assertThat(noul.instructions()).isNull();
		assertThat(noul.criteria()).isEqualTo(
				new NoulCriteria(JsonContent.of("Explicitly time-sensitive"), JsonContent.of("No urgency expressed")));
	}

	@Test
	void noulRequiresInstructionsOrCriteria() {
		// Confirmed against the live API: a noul with neither is rejected with 400
		// "Noul question must have criteria or instructions".
		assertThatIllegalArgumentException().isThrownBy(() -> Noul.builder().build())
			.withMessageContaining("instructions or criteria");
	}

	@Test
	void noulRejectsEmptyCriteriaDescriptions() {
		// whenTrue(JsonContent) already rejected null; the String and Map overloads let
		// it through as a JSON null, which only the server would have complained about.
		assertThatIllegalArgumentException().isThrownBy(() -> Noul.builder().instructions("x").whenTrue((String) null))
			.withMessageContaining("description must not be empty");
		assertThatIllegalArgumentException().isThrownBy(() -> Noul.builder().instructions("x").whenFalse(""))
			.withMessageContaining("description must not be empty");
		assertThatIllegalArgumentException().isThrownBy(() -> Noul.builder().instructions("x").whenTrue(Map.of()))
			.withMessageContaining("description must not be empty");
	}

	@Test
	void requestModelIsOptionalSoTheClientCanSupplyItsDefault() {
		SystemOneRequest request = SystemOneRequest.builder()
			.state("anything")
			.question("probe", Noul.of("Is this a probe?"))
			.build();

		assertThat(request.model()).isNull();
		assertThat(request.withModel(SystemOneModel.LATEST.id()).model()).isEqualTo(SystemOneModel.LATEST.id());
		// Naming a blank model explicitly is still a mistake worth catching early.
		assertThatIllegalArgumentException().isThrownBy(() -> SystemOneRequest.builder().model(" "))
			.withMessageContaining("model must not be empty");
	}

	@Test
	void requestRejectsAnEmptyQuestionSet() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SystemOneRequest.builder().state("anything").model(SystemOneModel.LATEST.id()).build())
			.withMessageContaining("at least one question");
	}

	@Test
	void choiceConstructorCopiesCriteria() {
		Map<String, JsonContent> criteria = new LinkedHashMap<>();
		criteria.put("billing", JsonContent.of("Payments"));

		Choice choice = new Choice(null, criteria);
		criteria.put("technical", JsonContent.of("Bugs"));

		assertThat(choice.criteria()).containsOnlyKeys("billing");
		assertThatThrownBy(() -> choice.criteria().put("sales", JsonContent.of("Upgrades")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void scoreConstructorCopiesCriteria() {
		List<JsonContent> criteria = new ArrayList<>(List.of(JsonContent.of("Low"), JsonContent.of("High")));

		Score score = new Score(null, criteria);
		criteria.add(JsonContent.of("Critical"));

		assertThat(score.criteria()).containsExactly(JsonContent.of("Low"), JsonContent.of("High"));
		assertThatThrownBy(() -> score.criteria().add(JsonContent.of("Critical")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void requestConstructorCopiesQuestions() {
		Map<String, Question> questions = new LinkedHashMap<>();
		questions.put("probe", Noul.of("Is this a probe?"));

		SystemOneRequest request = new SystemOneRequest(JsonContent.of("anything"), null, questions);
		questions.put("late", Noul.of("Was this added later?"));

		assertThat(request.questions()).containsOnlyKeys("probe");
		assertThatThrownBy(() -> request.questions().put("another", Noul.of("Can this be added?")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
