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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.decision.OllamaDecisionOptions;
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

	private static final ChoiceQuestion SPELL = ChoiceQuestion.builder()
		.text("Which spell fits the situation best?")
		.option("barrier", "For when a monster must be stopped from crossing")
		.option("fireball", "For when a monster is weak to fire")
		.option("light", "For when it is too dark to see")
		.option("summon_snacks", "For when hungry halflings are complaining")
		.build();

	@Autowired
	private OllamaDecisionModel decisionModel;

	@Test
	void answersAllQuestionKinds() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("A wizard stands on a narrow bridge. A demon made of flame, immune to fire, is about to cross it.")
			.question("spell", SPELL)
			.question("danger",
					YesNoQuestion.builder()
						.text("Is the wizard in mortal danger?")
						.whenYes("Something wants to eat, burn or crush him")
						.whenNo("The worst that can happen is running out of pipe-weed")
						.build())
			.question("drama",
					ScoreQuestion.builder()
						.text("How dramatic is this moment?")
						.level("mundane", "Like doing the dishes after second breakfast")
						.level("tense")
						.level("legendary")
						.build())
			.build());

		assertThat(response.getChoice("spell").getValue()).isEqualTo("barrier");
		assertThat(response.getChoice("spell").getProbabilities()).containsOnlyKeys("barrier", "fireball", "light",
				"summon_snacks");
		assertThat(response.getChoice("spell").getConfidence()).isBetween(0.0, 1.0);
		assertThat(response.getYesNo("danger").isYes()).isTrue();
		assertThat(response.getScore("drama").getValue()).isBetween(0.0, 2.0);
		assertThat(response.getScore("drama").getProbabilities()).hasSize(3);
		assertThat(response.getScore("drama").getNearestLevel()).isBetween(0, 2);
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			A wizard is in a pitch-black mine. It is too dark to see anything.    | light
			Four hungry halflings are complaining that they missed elevenses.    | summon_snacks
			""")
	void picksTheSpellForTheSituation(String situation, String expectedSpell) {
		DecisionResponse response = this.decisionModel.call(situation, Map.of("spell", SPELL));

		assertThat(response.getChoice("spell").getValue()).isEqualTo(expectedSpell);
	}

	@Test
	void decidesAboutFields() {
		Map<String, Object> characterSheet = new LinkedHashMap<>();
		characterSheet.put("name", "Grumbold Ironbeard");
		characterSheet.put("race", "dwarf");
		characterSheet.put("class", "barbarian");
		characterSheet.put("intelligence", 6);
		characterSheet.put("inventory", List.of("axe", "spare axe", "emergency axe", "a single cookie"));

		DecisionResponse response = this.decisionModel.call(DecisionInput.fields(characterSheet), Map.of("axe",
				YesNoQuestion.of("Is this character likely to solve the riddle at the door with an axe?")));

		assertThat(response.getYesNo("axe").getProbability()).isBetween(0.0, 1.0);
	}

	@Test
	void decidesAboutConversations() {
		DecisionResponse response = this.decisionModel.call(
				DecisionInput.conversation(List.of(
						new UserMessage("Can I sneak past the sleeping dragon if I put on my ring of invisibility?"),
						new AssistantMessage(
								"Roll for stealth. The ring hides you, but the dragon can still smell you."))),
				Map.of("answered",
						YesNoQuestion.of("Does the dungeon master's last message answer the player's question?")));

		assertThat(response.getYesNo("answered").getProbability()).isBetween(0.0, 1.0);
	}

	@Test
	void runtimeOptionsOverrideTheModel() {
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.text("The wizard arrived precisely when he meant to, two hours after the party started.")
			.question("late", YesNoQuestion.of("Is the wizard late?"))
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
		public TypeSafeClient systemOneClient(OllamaApi ollamaApi) {
			// Depends on ollamaApi so that the server is started before its URL is read.
			// A local Ollama ignores the API key, but the SDK requires one. A generous
			// timeout and no retries, since the first call loads the model.
			return TypeSafeClient.builder()
				.baseUrl(getOllamaBaseUrl())
				.apiKey("ollama")
				.timeout(Duration.ofMinutes(2))
				.retryPolicy(RetryPolicy.noRetry())
				.build();
		}

		@Bean
		public OllamaDecisionModel ollamaDecisionModel(OllamaApi ollamaApi, TypeSafeClient systemOneClient) {
			return OllamaDecisionModel.builder()
				.ollamaApi(ollamaApi)
				.systemOneClient(systemOneClient)
				.decisionOptions(OllamaDecisionOptions.builder().model(MODEL).build())
				.build();
		}

	}

}
