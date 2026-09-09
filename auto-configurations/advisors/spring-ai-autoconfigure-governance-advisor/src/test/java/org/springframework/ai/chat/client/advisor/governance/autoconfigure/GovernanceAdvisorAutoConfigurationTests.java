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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.advisor.governance.GovernanceAdvisor;
import org.springframework.ai.chat.client.advisor.governance.GovernanceMode;
import org.springframework.ai.chat.client.advisor.governance.PiiScanner;
import org.springframework.ai.chat.client.advisor.governance.SessionCostTracker;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GovernanceAdvisorAutoConfiguration}.
 *
 * @author Spring AI Contributors
 */
class GovernanceAdvisorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GovernanceAdvisorAutoConfiguration.class));

	// -------------------------------------------------------------------------
	// Disabled by default
	// -------------------------------------------------------------------------

	@Test
	void noBeansWhenDisabled() {
		this.contextRunner.run(ctx -> {
			assertThat(ctx).doesNotHaveBean(GovernanceAdvisor.class);
			assertThat(ctx).doesNotHaveBean(PiiScanner.class);
			assertThat(ctx).doesNotHaveBean(SessionCostTracker.class);
		});
	}

	// -------------------------------------------------------------------------
	// Enabled with defaults
	// -------------------------------------------------------------------------

	@Test
	void registersBeansWhenEnabled() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true").run(ctx -> {
			assertThat(ctx).hasSingleBean(GovernanceAdvisor.class);
			assertThat(ctx).hasSingleBean(PiiScanner.class);
			assertThat(ctx).hasSingleBean(SessionCostTracker.class);
		});
	}

	// -------------------------------------------------------------------------
	// Mode configuration
	// -------------------------------------------------------------------------

	@Test
	void defaultModeIsEnforce() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true").run(ctx -> {
			GovernanceAdvisorProperties props = ctx.getBean(GovernanceAdvisorProperties.class);
			assertThat(props.getMode()).isEqualTo(GovernanceMode.ENFORCE);
		});
	}

	@Test
	void modeCanBeSetToObserve() {
		this.contextRunner
			.withPropertyValues("spring.ai.advisor.governance.enabled=true",
					"spring.ai.advisor.governance.mode=observe")
			.run(ctx -> {
				GovernanceAdvisorProperties props = ctx.getBean(GovernanceAdvisorProperties.class);
				assertThat(props.getMode()).isEqualTo(GovernanceMode.OBSERVE);
			});
	}

	@Test
	void modeCanBeSetToMonitor() {
		this.contextRunner
			.withPropertyValues("spring.ai.advisor.governance.enabled=true",
					"spring.ai.advisor.governance.mode=monitor")
			.run(ctx -> {
				GovernanceAdvisorProperties props = ctx.getBean(GovernanceAdvisorProperties.class);
				assertThat(props.getMode()).isEqualTo(GovernanceMode.MONITOR);
			});
	}

	// -------------------------------------------------------------------------
	// PII configuration
	// -------------------------------------------------------------------------

	@Test
	void piiEnabledByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true").run(ctx -> {
			GovernanceAdvisorProperties props = ctx.getBean(GovernanceAdvisorProperties.class);
			assertThat(props.getPii().isEnabled()).isTrue();
		});
	}

	@Test
	void piiCanBeDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.advisor.governance.enabled=true",
					"spring.ai.advisor.governance.pii.enabled=false")
			.run(ctx -> {
				GovernanceAdvisorProperties props = ctx.getBean(GovernanceAdvisorProperties.class);
				assertThat(props.getPii().isEnabled()).isFalse();
			});
	}

	// -------------------------------------------------------------------------
	// Budget configuration
	// -------------------------------------------------------------------------

	@Test
	void budgetTokenLimitDefaultIsUnlimited() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true").run(ctx -> {
			SessionCostTracker tracker = ctx.getBean(SessionCostTracker.class);
			assertThat(tracker.hasLimit()).isFalse();
		});
	}

	@Test
	void budgetTokenLimitIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.advisor.governance.enabled=true",
					"spring.ai.advisor.governance.budget.token-limit=5000")
			.run(ctx -> {
				SessionCostTracker tracker = ctx.getBean(SessionCostTracker.class);
				assertThat(tracker.hasLimit()).isTrue();
				assertThat(tracker.getTokenLimit()).isEqualTo(5000L);
			});
	}

	// -------------------------------------------------------------------------
	// @ConditionalOnMissingBean — user-defined beans take precedence
	// -------------------------------------------------------------------------

	@Test
	void userDefinedGovernanceAdvisorTakesPrecedence() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true")
			.withUserConfiguration(CustomGovernanceAdvisorConfig.class)
			.run(ctx -> {
				assertThat(ctx).hasSingleBean(GovernanceAdvisor.class);
				GovernanceAdvisor advisor = ctx.getBean(GovernanceAdvisor.class);
				assertThat(advisor).isSameAs(ctx.getBean("customAdvisor"));
			});
	}

	@Test
	void userDefinedPiiScannerTakesPrecedence() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true")
			.withUserConfiguration(CustomPiiScannerConfig.class)
			.run(ctx -> assertThat(ctx).hasSingleBean(PiiScanner.class));
	}

	@Test
	void userDefinedCostTrackerTakesPrecedence() {
		this.contextRunner.withPropertyValues("spring.ai.advisor.governance.enabled=true")
			.withUserConfiguration(CustomCostTrackerConfig.class)
			.run(ctx -> {
				assertThat(ctx).hasSingleBean(SessionCostTracker.class);
				SessionCostTracker tracker = ctx.getBean(SessionCostTracker.class);
				assertThat(tracker.getTokenLimit()).isEqualTo(9999L);
			});
	}

	// -------------------------------------------------------------------------
	// Helper configurations
	// -------------------------------------------------------------------------

	@Configuration(proxyBeanMethods = false)
	static class CustomGovernanceAdvisorConfig {

		@Bean("customAdvisor")
		GovernanceAdvisor governanceAdvisor() {
			return GovernanceAdvisor.builder().mode(GovernanceMode.OBSERVE).build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomPiiScannerConfig {

		@Bean
		PiiScanner piiScanner() {
			return PiiScanner.builder().emailEnabled(false).build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomCostTrackerConfig {

		@Bean
		SessionCostTracker sessionCostTracker() {
			return new SessionCostTracker(9999L);
		}

	}

}
