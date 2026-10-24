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

package org.springframework.ai.autoconfigure.finops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TokenUsageObservabilityAutoConfiguration}.
 */
class TokenUsageObservabilityAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TokenUsageObservabilityAutoConfiguration.class));

	@Test
	void disabledByDefault() {
		this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(TokenUsageMeteringAdvisor.class)
				.doesNotHaveBean(ChatClientBuilderCustomizer.class));
	}

	@Test
	void enabledWhenPropertySetAndMeterRegistryPresent() {
		this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class)
			.withPropertyValues("spring.ai.finops.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(TokenUsageMeteringAdvisor.class)
				.hasSingleBean(ChatClientBuilderCustomizer.class)
				.hasSingleBean(FinOpsProperties.class));
	}

	@Test
	void notEnabledWithoutMeterRegistry() {
		this.contextRunner.withPropertyValues("spring.ai.finops.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean(TokenUsageMeteringAdvisor.class));
	}

	@Test
	void backsOffWhenCustomAdvisorPresent() {
		this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class, CustomAdvisorConfiguration.class)
			.withPropertyValues("spring.ai.finops.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(TokenUsageMeteringAdvisor.class)
				.hasBean("customAdvisor"));
	}

	@Configuration(proxyBeanMethods = false)
	static class MeterRegistryConfiguration {

		@Bean
		SimpleMeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomAdvisorConfiguration {

		@Bean
		TokenUsageMeteringAdvisor customAdvisor(SimpleMeterRegistry meterRegistry) {
			return new TokenUsageMeteringAdvisor(meterRegistry, new FinOpsProperties(true, 0.0, 0.0, 0.0));
		}

	}

}
