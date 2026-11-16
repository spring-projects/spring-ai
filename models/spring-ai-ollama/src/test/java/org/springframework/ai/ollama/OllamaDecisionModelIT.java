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

package org.springframework.ai.ollama;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.decision.OllamaDecisionOptions;
import org.springframework.ai.systemone.api.SystemOneClient;
import org.springframework.ai.systemone.api.SystemOneRetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OllamaDecisionModel}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest
class OllamaDecisionModelIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.D1_OMNI_600M.id();

	private static final ChoiceQuestion TOPIC = ChoiceQuestion.builder()
		.text("What is the message about?")
		.option("delivery", "Where an order is, or when it will arrive")
		.option("product", "A problem with an item the customer received")
		.option("account", "Login, address or payment details")
		.option("other", "None of the above")
		.build();

	@Autowired
	private OllamaDecisionModel decisionModel;

	@Test
	void answersAllQuestionKinds() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("My new headphones arrived yesterday, but the left side doesn't work. Can I get my money back?")
			.question("defect",
					YesNoQuestion.builder()
						.text("Does the customer report a defect?")
						.whenYes("Part of the item does not work as it should")
						.whenNo("The item works, and the message is about something else")
						.build())
			.question("topic", TOPIC)
			.question("satisfaction", ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy"))
			.build());

		assertThat(response.getAnswers().keySet()).containsExactly("defect", "topic", "satisfaction");
		assertThat(response.getYesNo("defect").isYes()).isTrue();
		assertThat(response.getChoice("topic").getValue()).isEqualTo("product");
		assertThat(response.getChoice("topic").getProbabilities()).containsOnlyKeys("delivery", "product", "account",
				"other");
		assertThat(response.getChoice("topic").getConfidence()).isBetween(0.0, 1.0);
		assertThat(response.getScore("satisfaction").getValue()).isBetween(0.0, 2.0);
		assertThat(response.getScore("satisfaction").getProbabilities()).hasSize(3);
		assertThat(response.getScore("satisfaction").getNearestLevel()).isLessThanOrEqualTo(1);
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Where is my order? I ordered it two weeks ago.                     | delivery
			I can't log in to my account since I changed my password.          | account
			""")
	void picksTheTopicOfTheMessage(String message, String expectedTopic) {
		DecisionResponse response = this.decisionModel.call(message, Map.of("topic", TOPIC));

		assertThat(response.getChoice("topic").getValue()).isEqualTo(expectedTopic);
	}

	@Test
	void decidesAboutFields() {
		Map<String, Object> customer = new LinkedHashMap<>();
		customer.put("message", "This is the third time my order arrives late.");
		customer.put("orders", 12);
		customer.put("late_deliveries", 3);

		DecisionResponse response = this.decisionModel.call(DecisionInput.fields(customer),
				Map.of("repeated", YesNoQuestion.of("Does the customer say that the problem happened before?")));

		assertThat(response.getYesNo("repeated").getProbability()).isBetween(0.0, 1.0);
	}

	@Test
	void decidesAboutConversations() {
		DecisionResponse response = this.decisionModel.call(
				DecisionInput.conversation(List.of(new UserMessage("Can I return the headphones?"),
						new AssistantMessage("Your order will arrive on Friday."))),
				Map.of("answered", YesNoQuestion.of("Does the last assistant message answer the user's question?")));

		assertThat(response.getYesNo("answered").getProbability()).isBetween(0.0, 1.0);
	}

	@Test
	void runtimeOptionsOverrideTheModel() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("Where is my order? I ordered it two weeks ago.")
			.question("topic", TOPIC)
			.options(OllamaDecisionOptions.builder().model(MODEL).build())
			.build());

		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public SystemOneClient systemOneClient(OllamaApi ollamaApi) {
			// Depends on ollamaApi so that the server is started before its URL is read.
			// A local Ollama ignores the API key, but the SDK requires one. A generous
			// timeout and no retries, since the first call loads the model.
			return SystemOneClient.builder()
				.baseUrl(getOllamaBaseUrl())
				.apiKey("ollama")
				.timeout(Duration.ofMinutes(2))
				.retryTemplate(SystemOneRetryUtils.noRetryTemplate())
				.build();
		}

		@Bean
		public OllamaDecisionModel ollamaDecisionModel(OllamaApi ollamaApi, SystemOneClient systemOneClient) {
			return OllamaDecisionModel.builder()
				.ollamaApi(ollamaApi)
				.systemOneClient(systemOneClient)
				.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
				.build();
		}

	}

}
