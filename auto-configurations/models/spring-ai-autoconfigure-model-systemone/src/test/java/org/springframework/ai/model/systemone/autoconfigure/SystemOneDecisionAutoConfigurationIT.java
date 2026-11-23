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

package org.springframework.ai.model.systemone.autoconfigure;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.systemone.SystemOneDecisionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live test of the auto-configured {@link SystemOneDecisionModel} against the server the
 * {@code SYSTEMONE_API_KEY}, {@code SYSTEMONE_BASE_URL} and {@code SYSTEMONE_MODEL}
 * variables point at.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "SYSTEMONE_API_KEY", matches = ".+")
class SystemOneDecisionAutoConfigurationIT {

	@Test
	void theAutoConfiguredModelAnswers() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
			.withPropertyValues("spring.ai.systemone.api-key=" + System.getenv("SYSTEMONE_API_KEY"),
					"spring.ai.systemone.timeout=2m", "spring.ai.systemone.retry.max-retries=0");
		String baseUrl = System.getenv("SYSTEMONE_BASE_URL");
		if (baseUrl != null) {
			contextRunner = contextRunner.withPropertyValues("spring.ai.systemone.base-url=" + baseUrl);
		}
		String model = System.getenv("SYSTEMONE_MODEL");
		if (model != null) {
			contextRunner = contextRunner.withPropertyValues("spring.ai.systemone.decision.model=" + model);
		}

		contextRunner.run(context -> {
			DecisionResponse response = context.getBean(SystemOneDecisionModel.class)
				.call("Help! My payouts have been failing for 3 days.",
						Map.of("urgent", YesNoQuestion.of("Does this convey urgency?")));

			assertThat(response.getYesNo("urgent").getProbability()).isBetween(0.0, 1.0);
		});
	}

}
