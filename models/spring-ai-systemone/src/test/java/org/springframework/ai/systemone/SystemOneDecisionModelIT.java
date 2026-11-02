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
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live tests for {@link SystemOneDecisionModel} against whatever System One server the
 * {@code TYPESAFE_BASE_URL}, {@code TYPESAFE_API_KEY} and {@code TYPESAFE_DEFAULT_MODEL}
 * variables point at: TypeSafe Jev by default, or a local Laya or Ollama. Only the shape
 * of the answers is asserted, since the models answer differently.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class SystemOneDecisionModelIT {

	// A generous timeout and no retries: a local server loads the model on the first
	// call.
	private final SystemOneDecisionModel decisionModel = SystemOneDecisionModel
		.builder(TypeSafeClient.builder().timeout(Duration.ofMinutes(2)).retryPolicy(RetryPolicy.noRetry()).build())
		.build();

	@Test
	void answersAllThreeQuestionKinds() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("Help! My payouts are late again and nobody answers my emails.")
			.question("urgent",
					YesNoQuestion.builder()
						.text("Is this request urgent?")
						.whenYes("Money or access is blocked")
						.whenNo("The customer is asking a general question")
						.build())
			.question("team",
					ChoiceQuestion.builder()
						.text("Which team should handle this request?")
						.option("billing", "Invoices, charges, payouts and refunds")
						.option("technical", "Bugs, outages and integrations")
						.option("sales", "Quotes and new purchases")
						.build())
			.question("mood", ScoreQuestion.of("How is the customer's mood?", "calm", "annoyed", "angry"))
			.build());

		assertThat(response.getYesNo("urgent").getProbability()).isBetween(0.0, 1.0);
		assertThat(response.getChoice("team").getValue()).isIn("billing", "technical", "sales");
		assertThat(response.getChoice("team").getProbabilities()).containsOnlyKeys("billing", "technical", "sales");
		assertThat(response.getScore("mood").getValue()).isBetween(0.0, 2.0);
		assertThat(response.getScore("mood").getProbabilities()).hasSize(3);
		assertThat(response.getScore("mood").getNearestLevel()).isBetween(0, 2);
		assertThat(response.getMetadata().getModel()).isNotBlank();
	}

	@Test
	void decidesAboutFieldsAndConversations() {
		Map<String, Object> ticket = new LinkedHashMap<>();
		ticket.put("subject", "Charged twice");
		ticket.put("body", "My card was charged twice for the same order.");
		ticket.put("tier", "enterprise");

		DecisionResponse fields = this.decisionModel.call(DecisionInput.fields(ticket),
				Map.of("refund", YesNoQuestion.of("Is the customer asking for a refund?")));
		DecisionResponse conversation = this.decisionModel.call(
				DecisionInput.conversation(List.of(new UserMessage("Can I get a refund after 30 days?"),
						new AssistantMessage("Refunds are available within 14 days of purchase."))),
				Map.of("answered", YesNoQuestion.of("Does the last assistant message answer the user's question?")));

		assertThat(fields.getYesNo("refund").getProbability()).isBetween(0.0, 1.0);
		assertThat(conversation.getYesNo("answered").getProbability()).isBetween(0.0, 1.0);
	}

}
