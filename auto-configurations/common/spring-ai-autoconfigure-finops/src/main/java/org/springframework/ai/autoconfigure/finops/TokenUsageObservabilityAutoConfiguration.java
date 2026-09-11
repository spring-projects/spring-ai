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

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI FinOps token-usage observability.
 *
 * <p>
 * Wires a {@link TokenUsageMeteringAdvisor} into every {@link ChatClient.Builder} in the
 * application context. The advisor intercepts the raw
 * {@link org.springframework.ai.chat.model.ChatResponse ChatResponse} before
 * structured-output parsing discards it, reads the
 * {@link org.springframework.ai.chat.metadata.Usage Usage}, and publishes Micrometer
 * metrics:
 *
 * <pre>
 * spring.ai.token.usage{model, operation, ai.token.type=[prompt|completion|total|cached|cache_write]}
 * spring.ai.token.cost.estimated.usd{model, tier=[prompt|completion]}
 * </pre>
 *
 * <p>
 * Enable via: {@code spring.ai.finops.enabled=true}
 *
 * <p>
 * Addresses:
 * <ul>
 * <li><a href="https://github.com/spring-projects/spring-ai/issues/3895">#3895</a></li>
 * <li><a href="https://github.com/spring-projects/spring-ai/issues/186">#186</a></li>
 * <li><a href="https://github.com/spring-projects/spring-ai/issues/1506">#1506</a></li>
 * </ul>
 */
@AutoConfiguration(afterName = { "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
		"org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration" })
@ConditionalOnClass({ ChatClient.class, MeterRegistry.class })
@ConditionalOnProperty(prefix = "spring.ai.finops", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(FinOpsProperties.class)
public class TokenUsageObservabilityAutoConfiguration {

	/**
	 * Creates a {@link TokenUsageMeteringAdvisor} that captures token usage from each
	 * {@link org.springframework.ai.chat.model.ChatResponse ChatResponse} and records
	 * Micrometer metrics.
	 * @param meterRegistry the Micrometer registry to publish metrics to
	 * @param properties the FinOps configuration properties
	 * @return the metering advisor
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	public TokenUsageMeteringAdvisor tokenUsageMeteringAdvisor(MeterRegistry meterRegistry,
			FinOpsProperties properties) {
		return new TokenUsageMeteringAdvisor(meterRegistry, properties);
	}

	/**
	 * Post-processes every {@link ChatClient.Builder} to install the metering advisor.
	 * This is the idiomatic Spring AI extension point -- it runs before the builder is
	 * exposed to application code so every chat call is automatically instrumented.
	 * @param advisor the metering advisor to install
	 * @return the builder customizer
	 */
	@Bean
	@ConditionalOnBean(TokenUsageMeteringAdvisor.class)
	public ChatClientBuilderCustomizer finOpsMeteringCustomizer(TokenUsageMeteringAdvisor advisor) {
		return chatClientBuilder -> chatClientBuilder.defaultAdvisors(advisor);
	}

}
