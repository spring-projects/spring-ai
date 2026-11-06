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
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.TextPart;
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
	void docsExample() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("My new headphones arrived yesterday, but the left side doesn't work. Can I get my money back?")
			.question("defect",
					YesNoQuestion.builder()
						.text("Does the customer report a defect?")
						.whenYes("Part of the item does not work as it should")
						.whenNo("The item works, and the message is about something else")
						.build())
			.question("topic",
					ChoiceQuestion.builder()
						.text("What is the message about?")
						.option("delivery", "Where an order is, or when it will arrive")
						.option("product", "A problem with an item the customer received")
						.option("account", "Login, address or payment details")
						.option("other", "None of the above")
						.build())
			.question("satisfaction", ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy"))
			.build());

		assertThat(response.getAnswers().keySet()).containsExactly("defect", "topic", "satisfaction");
		assertThat(response.getYesNo("defect").isYes()).isTrue();
		assertThat(response.getChoice("topic").getValue()).isEqualTo("product");
		assertThat(response.getScore("satisfaction").getNearestLevel()).isLessThanOrEqualTo(1);
	}

	@Test
	void yesNo() {
		var question = YesNoQuestion.of("Does the customer report a defect?");

		var defect = this.decisionModel.call(
				"My new headphones arrived yesterday, but the left side doesn't work. Can I get my money back?",
				Map.of("defect", question));
		var noDefect = this.decisionModel.call("Where is my order? I ordered it two weeks ago.",
				Map.of("defect", question));

		assertThat(defect.getYesNo("defect").getProbability())
			.isGreaterThan(noDefect.getYesNo("defect").getProbability());
		assertThat(defect.getMetadata().getId()).isNotBlank();
		assertThat(defect.getMetadata().getModel()).isNotBlank();
		assertThat(defect.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@Test
	void choice() {
		var response = this.decisionModel.call("Where is my order? I ordered it two weeks ago.",
				Map.of("topic",
						ChoiceQuestion.builder()
							.text("What is the message about?")
							.option("delivery", "Where an order is, or when it will arrive")
							.option("product", "A problem with an item the customer received")
							.option("account", "Login, address or payment details")
							.build()));

		assertThat(response.getChoice("topic").getValue()).isEqualTo("delivery");
		assertThat(response.getChoice("topic").getProbabilities()).containsKeys("delivery", "product", "account");
		assertThat(response.getChoice("topic").getProbability("delivery")).isGreaterThan(0.5);
	}

	@Test
	void score() {
		var question = ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy");

		var happy = this.decisionModel.call("The headphones are great, and they arrived a day early. Thank you!",
				Map.of("satisfaction", question));
		var unhappy = this.decisionModel.call("This is the third time my order arrives late. I am really disappointed.",
				Map.of("satisfaction", question));

		assertThat(happy.getScore("satisfaction").getValue())
			.isGreaterThan(unhappy.getScore("satisfaction").getValue());
		assertThat(happy.getScore("satisfaction").getProbabilities()).hasSize(3);
	}

	@Test
	void severalQuestionsWithFieldsInput() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("message", "This is the third time my order arrives late.");
		fields.put("orders", 12);
		fields.put("late_deliveries", 3);

		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question("repeated", YesNoQuestion.of("Does the customer say that the problem happened before?"))
			.question("topic", ChoiceQuestion.of("What is the message about?", "delivery", "product", "account"))
			.question("satisfaction", ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy"))
			.build());

		assertThat(response.getResults()).hasSize(3);
		assertThat(response.getAnswers().keySet()).containsExactly("repeated", "topic", "satisfaction");
		assertThat(response.getYesNo("repeated").isYes()).isTrue();
		assertThat(response.getChoice("topic").getValue()).isEqualTo("delivery");
		assertThat(response.getScore("satisfaction").getValue()).isLessThan(1.5);
	}

	@Test
	void conversationInput() {
		var question = YesNoQuestion.of("Does the last assistant message answer the user's question?");

		var answered = this.decisionModel.call(
				DecisionInput.conversation(List.of(new UserMessage("Can I return the headphones?"),
						new AssistantMessage("Yes, you can return them within 30 days of delivery."))),
				Map.of("answered", question));
		var notAnswered = this.decisionModel
			.call(DecisionInput.conversation(List.of(new UserMessage("Can I return the headphones?"),
					new AssistantMessage("Your order will arrive on Friday."))), Map.of("answered", question));

		assertThat(answered.getYesNo("answered").isYes()).isTrue();
		assertThat(notAnswered.getYesNo("answered").isYes()).isFalse();
	}

	@Test
	void imageInput() {
		var image = new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png"));

		var response = this.decisionModel.call(
				DecisionInput.content(TextPart.of("Describe the picture"), MediaPart.of(image)),
				Map.of("fruit",
						ChoiceQuestion.builder()
							.text("What does the picture show?")
							.options("fruit", "vehicle", "animal")
							.build()));

		assertThat(response.getChoice("fruit").getValue()).isEqualTo("fruit");
	}

}
