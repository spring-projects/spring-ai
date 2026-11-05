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

package org.springframework.ai.systemone;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.systemone.api.SystemOneClient;
import org.springframework.ai.systemone.api.SystemOneRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live tests for {@link SystemOneDecisionModel} against whatever System One server the
 * {@code SYSTEMONE_BASE_URL}, {@code SYSTEMONE_API_KEY} and {@code SYSTEMONE_MODEL}
 * variables point at: the hosted service by default, or a local Laya or Ollama. Only the
 * shape of the answers is asserted, since the models answer differently.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "SYSTEMONE_API_KEY", matches = ".+")
class SystemOneDecisionModelIT {

	// A generous timeout and no retries: a local server loads the model on the first
	// call.
	private final SystemOneDecisionModel decisionModel = SystemOneDecisionModel.builder(client()).build();

	private static SystemOneClient client() {
		SystemOneClient.Builder builder = SystemOneClient.builder()
			.apiKey(System.getenv("SYSTEMONE_API_KEY"))
			.timeout(Duration.ofMinutes(2))
			.retryPolicy(SystemOneRetryPolicy.noRetry());
		String baseUrl = System.getenv("SYSTEMONE_BASE_URL");
		if (baseUrl != null) {
			builder.baseUrl(baseUrl);
		}
		String model = System.getenv("SYSTEMONE_MODEL");
		if (model != null) {
			builder.defaultModel(model);
		}
		return builder.build();
	}

	@Test
	void answersAllThreeQuestionKinds() {
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

		assertThat(response.getYesNo("defect").getProbability()).isBetween(0.0, 1.0);
		assertThat(response.getChoice("topic").getValue()).isIn("delivery", "product", "account", "other");
		assertThat(response.getChoice("topic").getProbabilities()).containsOnlyKeys("delivery", "product", "account",
				"other");
		assertThat(response.getScore("satisfaction").getValue()).isBetween(0.0, 2.0);
		assertThat(response.getScore("satisfaction").getProbabilities()).hasSize(3);
		assertThat(response.getScore("satisfaction").getNearestLevel()).isBetween(0, 2);
		assertThat(response.getMetadata().getModel()).isNotBlank();
	}

	@Test
	void decidesAboutFieldsAndConversations() {
		Map<String, Object> customer = new LinkedHashMap<>();
		customer.put("message", "This is the third time my order arrives late.");
		customer.put("orders", 12);
		customer.put("late_deliveries", 3);

		DecisionResponse fields = this.decisionModel.call(DecisionInput.fields(customer),
				Map.of("repeated", YesNoQuestion.of("Does the customer say that the problem happened before?")));
		DecisionResponse conversation = this.decisionModel.call(
				DecisionInput.conversation(List.of(new UserMessage("Can I return the headphones?"),
						new AssistantMessage("Your order will arrive on Friday."))),
				Map.of("answered", YesNoQuestion.of("Does the last assistant message answer the user's question?")));

		assertThat(fields.getYesNo("repeated").getProbability()).isBetween(0.0, 1.0);
		assertThat(conversation.getYesNo("answered").getProbability()).isBetween(0.0, 1.0);
	}

}
