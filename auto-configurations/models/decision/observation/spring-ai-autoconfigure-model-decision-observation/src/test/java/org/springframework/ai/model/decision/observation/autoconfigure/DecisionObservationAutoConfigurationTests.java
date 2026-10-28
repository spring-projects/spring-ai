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

package org.springframework.ai.model.decision.observation.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.observation.DecisionModelMeterObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DecisionObservationAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
class DecisionObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(DecisionObservationAutoConfiguration.class));

	@Test
	void meterHandlerNeedsMeterRegistry() {
		this.contextRunner
			.run(context -> assertThat(context).doesNotHaveBean(DecisionModelMeterObservationHandler.class));
	}

	@Test
	void meterHandlerWithMeterRegistry() {
		this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(DecisionModelMeterObservationHandler.class));
	}

	@Test
	void customMeterHandlerWins() {
		this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class, CustomHandlerConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(DecisionModelMeterObservationHandler.class)
				.hasBean("customDecisionModelMeterObservationHandler"));
	}

	@Configuration(proxyBeanMethods = false)
	static class MeterRegistryConfiguration {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomHandlerConfiguration {

		@Bean
		DecisionModelMeterObservationHandler customDecisionModelMeterObservationHandler(MeterRegistry meterRegistry) {
			return new DecisionModelMeterObservationHandler(meterRegistry);
		}

	}

}
