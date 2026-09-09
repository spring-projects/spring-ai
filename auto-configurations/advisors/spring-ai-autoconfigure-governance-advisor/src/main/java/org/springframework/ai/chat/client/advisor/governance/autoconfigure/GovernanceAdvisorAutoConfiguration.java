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

package org.springframework.ai.chat.client.advisor.governance.autoconfigure;

import org.springframework.ai.chat.client.advisor.governance.GovernanceAdvisor;
import org.springframework.ai.chat.client.advisor.governance.PiiScanner;
import org.springframework.ai.chat.client.advisor.governance.SessionCostTracker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link GovernanceAdvisor}.
 *
 * <p>
 * Activated when {@code spring.ai.advisor.governance.enabled=true} is set and
 * {@link GovernanceAdvisor} is on the classpath. All advisor components — the
 * {@link PiiScanner}, the {@link SessionCostTracker}, and the {@link GovernanceAdvisor}
 * itself — are registered as beans and can be individually replaced by declaring beans of
 * the same type in the application context.
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(GovernanceAdvisor.class)
@EnableConfigurationProperties(GovernanceAdvisorProperties.class)
@ConditionalOnProperty(prefix = GovernanceAdvisorProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class GovernanceAdvisorAutoConfiguration {

	/**
	 * Registers the {@link PiiScanner} bean.
	 * <p>
	 * Skipped when the application already declares a {@link PiiScanner} bean.
	 */
	@Bean
	@ConditionalOnMissingBean
	PiiScanner piiScanner(GovernanceAdvisorProperties properties) {
		GovernanceAdvisorProperties.Pii pii = properties.getPii();
		return PiiScanner.builder()
			.ssnEnabled(pii.isSsnEnabled())
			.creditCardEnabled(pii.isCreditCardEnabled())
			.emailEnabled(pii.isEmailEnabled())
			.apiKeyEnabled(pii.isApiKeyEnabled())
			.build();
	}

	/**
	 * Registers the {@link SessionCostTracker} bean.
	 * <p>
	 * Skipped when the application already declares a {@link SessionCostTracker} bean.
	 */
	@Bean
	@ConditionalOnMissingBean
	SessionCostTracker sessionCostTracker(GovernanceAdvisorProperties properties) {
		return new SessionCostTracker(properties.getBudget().getTokenLimit());
	}

	/**
	 * Registers the {@link GovernanceAdvisor} bean.
	 * <p>
	 * Skipped when the application already declares a {@link GovernanceAdvisor} bean.
	 */
	@Bean
	@ConditionalOnMissingBean
	GovernanceAdvisor governanceAdvisor(GovernanceAdvisorProperties properties, PiiScanner piiScanner,
			SessionCostTracker sessionCostTracker) {
		return GovernanceAdvisor.builder()
			.mode(properties.getMode())
			.piiScanner(piiScanner)
			.piiEnabled(properties.getPii().isEnabled())
			.costTracker(sessionCostTracker)
			.budgetEnabled(properties.getBudget().isEnabled())
			.sessionIdContextKey(properties.getSessionIdContextKey())
			.order(properties.getAdvisorOrder())
			.build();
	}

}
