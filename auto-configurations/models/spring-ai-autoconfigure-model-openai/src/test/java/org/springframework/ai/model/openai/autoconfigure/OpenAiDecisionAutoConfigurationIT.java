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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiDecisionAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiDecisionAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(OpenAiDecisionAutoConfiguration.class));

	@Test
	void decide() {
		this.contextRunner.run(context -> {
			OpenAiDecisionModel decisionModel = context.getBean(OpenAiDecisionModel.class);
			var response = decisionModel.call("WIN A FREE CRUISE!!! Click here now",
					Map.of("spam", YesNoQuestion.of("Is this message spam?")));
			assertThat(response.getYesNo("spam").isYes()).isTrue();
		});
	}

}
