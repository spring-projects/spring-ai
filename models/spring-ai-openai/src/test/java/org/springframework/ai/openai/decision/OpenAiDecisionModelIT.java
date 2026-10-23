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

package org.springframework.ai.openai.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiDecisionModel}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiDecisionModelIT {

	@Autowired
	private OpenAiDecisionModel decisionModel;

	@Test
	void yesNo() {
		var question = YesNoQuestion.of("Is this message spam?");

		var spam = this.decisionModel.call("WIN A FREE CRUISE!!! Click here now to claim your prize",
				Map.of("spam", question));
		var notSpam = this.decisionModel.call("Hi Anna, are we still on for lunch tomorrow at noon?",
				Map.of("spam", question));

		assertThat(spam.getYesNo("spam").getProbability()).isGreaterThan(notSpam.getYesNo("spam").getProbability());
		assertThat(spam.getMetadata().getModel()).isNotBlank();
		assertThat(spam.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@Test
	void choice() {
		var response = this.decisionModel.call("My invoice shows a charge I never authorised",
				Map.of("team",
						ChoiceQuestion.builder()
							.text("Which team should handle this request?")
							.option("billing", "Invoices, charges and refunds")
							.option("technical", "Bugs and outages")
							.option("sales", "Quotes and new purchases")
							.build()));

		assertThat(response.getChoice("team").getValue()).isEqualTo("billing");
		assertThat(response.getChoice("team").getProbabilities()).containsKeys("billing", "technical", "sales");
		assertThat(response.getChoice("team").getProbability("billing")).isGreaterThan(0.5);
	}

	@Test
	void score() {
		var question = ScoreQuestion.builder()
			.text("How urgent is this request?")
			.levels("low", "medium", "high")
			.build();

		var urgent = this.decisionModel.call("Our production site is down and customers cannot pay!",
				Map.of("urgency", question));
		var relaxed = this.decisionModel.call("Whenever you have a moment, could you share the 2023 brochure?",
				Map.of("urgency", question));

		assertThat(urgent.getScore("urgency").getValue()).isGreaterThan(relaxed.getScore("urgency").getValue());
		assertThat(urgent.getScore("urgency").getProbabilities()).hasSize(3);
	}

	@Test
	void severalQuestionsWithFieldsInput() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("subject", "Payouts late again");
		fields.put("body", "This is the third time this month my payout is delayed. Fix it now.");
		fields.put("customer_since", 2019);

		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question("urgent", YesNoQuestion.of("Is the request urgent?"))
			.question("team",
					ChoiceQuestion.builder()
						.text("Which team should handle it?")
						.options("billing", "technical")
						.build())
			.question("mood",
					ScoreQuestion.builder()
						.text("How is the customer's mood?")
						.levels("calm", "annoyed", "angry")
						.build())
			.build());

		assertThat(response.getResults()).hasSize(3);
		assertThat(response.getAnswers().keySet()).containsExactly("urgent", "team", "mood");
		assertThat(response.getYesNo("urgent").isYes()).isTrue();
		assertThat(response.getChoice("team").getValue()).isEqualTo("billing");
		assertThat(response.getScore("mood").getValue()).isGreaterThan(0.5);
	}

	@Test
	void conversationInput() {
		var messages = List.of(new UserMessage("What is the capital of France?"),
				new AssistantMessage("The capital of France is Paris."));

		var response = this.decisionModel.call(DecisionInput.conversation(messages),
				Map.of("correct", YesNoQuestion.of("Is the assistant's answer correct?")));

		assertThat(response.getYesNo("correct").isYes()).isTrue();
	}

	@Test
	void imageInput() {
		var image = new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png"));

		var response = this.decisionModel.call(DecisionInput.text("Describe the picture", List.of(image)),
				Map.of("fruit",
						ChoiceQuestion.builder()
							.text("What does the picture show?")
							.options("fruit", "vehicle", "animal")
							.build()));

		assertThat(response.getChoice("fruit").getValue()).isEqualTo("fruit");
	}

}
