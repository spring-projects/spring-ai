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

package org.springframework.ai.model.ollama.autoconfigure;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.ollama.OllamaDecisionModel;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OllamaDecisionAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
class OllamaDecisionAutoConfigurationIT extends BaseOllamaIT {

	private static final String MODEL_NAME = OllamaModel.D1_OMNI_600M.id();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.ollama.decision.model=" + MODEL_NAME,
				"spring.ai.ollama.base-url=" + getBaseUrl())
		.withConfiguration(ollamaAutoConfig(OllamaDecisionAutoConfiguration.class));

	@BeforeAll
	static void beforeAll() {
		initializeOllama(MODEL_NAME);
	}

	@Test
	void decision() {
		this.contextRunner.run(context -> {
			OllamaDecisionModel decisionModel = context.getBean(OllamaDecisionModel.class);

			DecisionResponse response = decisionModel.call(DecisionPrompt.builder()
				.text("Four hungry halflings are complaining that they missed elevenses.")
				.question("spell",
						ChoiceQuestion.builder()
							.text("Which spell fits the situation best?")
							.option("light", "For when it is too dark to see")
							.option("summon_snacks", "For when hungry halflings are complaining")
							.build())
				.question("danger", YesNoQuestion.of("Is anyone in mortal danger?"))
				.build());

			assertThat(response.getChoice("spell").getValue()).isEqualTo("summon_snacks");
			assertThat(response.getYesNo("danger").getProbability()).isBetween(0.0, 1.0);
			assertThat(response.getMetadata().getModel()).isEqualTo(MODEL_NAME);
		});
	}

	@Test
	void decisionWithPull() {
		this.contextRunner.withPropertyValues("spring.ai.ollama.init.pull-model-strategy=when_missing").run(context -> {
			OllamaDecisionModel decisionModel = context.getBean(OllamaDecisionModel.class);

			DecisionResponse response = decisionModel.call(DecisionPrompt.builder()
				.text("A dwarf is about to answer a riddle with an axe.")
				.question("axe", YesNoQuestion.of("Is the dwarf about to use an axe?"))
				.build());

			assertThat(response.getYesNo("axe").getProbability()).isBetween(0.0, 1.0);
		});
	}

}
