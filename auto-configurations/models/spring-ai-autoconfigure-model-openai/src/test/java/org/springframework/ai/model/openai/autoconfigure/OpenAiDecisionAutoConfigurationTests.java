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

import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.observation.DecisionModelObservationContext;
import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiDecisionOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiDecisionAutoConfiguration} and
 * {@link OpenAiDecisionProperties}.
 *
 * @author Thomas Vitale
 */
class OpenAiDecisionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL")
		.withConfiguration(AutoConfigurations.of(OpenAiDecisionAutoConfiguration.class));

	@Test
	void decisionModelIsCreatedByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(OpenAiDecisionModel.class);
			assertThat(context.getBean(OpenAiDecisionModel.class).getOptions().getModel())
				.isEqualTo(OpenAiDecisionOptions.DEFAULT_DECISION_MODEL);
		});
	}

	@Test
	void decisionModelActivation() {
		this.contextRunner.withPropertyValues("spring.ai.model.decision=none").run(context -> {
			assertThat(context).doesNotHaveBean(OpenAiDecisionProperties.class);
			assertThat(context).doesNotHaveBean(OpenAiDecisionModel.class);
		});

		this.contextRunner.withPropertyValues("spring.ai.model.decision=openai")
			.run(context -> assertThat(context).hasSingleBean(OpenAiDecisionModel.class));
	}

	@Test
	void decisionProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.decision.base-url=http://DECISION.URL",
					"spring.ai.openai.decision.api-key=DECISION_KEY", "spring.ai.openai.decision.model=MODEL_XYZ",
					"spring.ai.openai.decision.safety-identifier=user-123")
			.run(context -> {
				var properties = context.getBean(OpenAiDecisionProperties.class);
				assertThat(properties.getBaseUrl()).isEqualTo("http://DECISION.URL");
				assertThat(properties.getApiKey()).isEqualTo("DECISION_KEY");
				assertThat(properties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(properties.getSafetyIdentifier()).isEqualTo("user-123");

				var options = context.getBean(OpenAiDecisionModel.class).getOptions();
				assertThat(options.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(options.getSafetyIdentifier()).isEqualTo("user-123");
			});
	}

	@Test
	void customObservationConventionIsApplied() {
		this.contextRunner.withUserConfiguration(CustomConventionConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(OpenAiDecisionModel.class)
				.hasSingleBean(DecisionModelObservationConvention.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConventionConfiguration {

		@Bean
		DecisionModelObservationConvention customDecisionModelObservationConvention() {
			return new DecisionModelObservationConvention() {
				@Override
				public String getName() {
					return "custom";
				}

				@Override
				public String getContextualName(DecisionModelObservationContext context) {
					return "custom";
				}
			};
		}

	}

}
