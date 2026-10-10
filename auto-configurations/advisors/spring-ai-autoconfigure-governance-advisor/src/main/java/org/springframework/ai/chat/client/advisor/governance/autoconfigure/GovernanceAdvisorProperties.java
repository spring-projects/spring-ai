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
import org.springframework.ai.chat.client.advisor.governance.GovernanceMode;
import org.springframework.ai.chat.client.advisor.governance.SessionCostTracker;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link GovernanceAdvisorAutoConfiguration}.
 *
 * <p>
 * All properties share the prefix {@code spring.ai.advisor.governance}.
 *
 * <p>
 * Example {@code application.yml}: <pre>{@code
 * spring:
 *   ai:
 *     advisor:
 *       governance:
 *         enabled: true
 *         mode: enforce
 *         pii:
 *           enabled: true
 *         budget:
 *           enabled: true
 *           token-limit: 10000
 * }</pre>
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
@ConfigurationProperties(GovernanceAdvisorProperties.CONFIG_PREFIX)
public class GovernanceAdvisorProperties {

	public static final String CONFIG_PREFIX = "spring.ai.advisor.governance";

	/**
	 * Whether to register the {@link GovernanceAdvisor} bean automatically.
	 */
	private boolean enabled = false;

	/**
	 * Governance enforcement mode. One of {@code observe}, {@code monitor}, or
	 * {@code enforce} (default: {@code enforce}).
	 */
	private GovernanceMode mode = GovernanceMode.ENFORCE;

	/**
	 * Advisor order in the advisor chain (default: {@code 0}).
	 */
	private int advisorOrder = 0;

	/**
	 * Key in the request context that holds the session / conversation identifier used
	 * for budget tracking. Defaults to
	 * {@value GovernanceAdvisor#DEFAULT_SESSION_ID_CONTEXT_KEY}.
	 */
	private String sessionIdContextKey = GovernanceAdvisor.DEFAULT_SESSION_ID_CONTEXT_KEY;

	private final Pii pii = new Pii();

	private final Budget budget = new Budget();

	// -------------------------------------------------------------------------
	// Getters / setters
	// -------------------------------------------------------------------------

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public GovernanceMode getMode() {
		return this.mode;
	}

	public void setMode(GovernanceMode mode) {
		this.mode = mode;
	}

	public int getAdvisorOrder() {
		return this.advisorOrder;
	}

	public void setAdvisorOrder(int advisorOrder) {
		this.advisorOrder = advisorOrder;
	}

	public String getSessionIdContextKey() {
		return this.sessionIdContextKey;
	}

	public void setSessionIdContextKey(String sessionIdContextKey) {
		this.sessionIdContextKey = sessionIdContextKey;
	}

	public Pii getPii() {
		return this.pii;
	}

	public Budget getBudget() {
		return this.budget;
	}

	// -------------------------------------------------------------------------
	// Nested property classes
	// -------------------------------------------------------------------------

	/**
	 * PII-scanning sub-properties.
	 */
	public static class Pii {

		/**
		 * Whether PII scanning is enabled (default: {@code true}).
		 */
		private boolean enabled = true;

		/**
		 * Whether to scan for US Social Security Numbers (default: {@code true}).
		 */
		private boolean ssnEnabled = true;

		/**
		 * Whether to scan for credit/debit card numbers (default: {@code true}).
		 */
		private boolean creditCardEnabled = true;

		/**
		 * Whether to scan for e-mail addresses (default: {@code true}).
		 */
		private boolean emailEnabled = true;

		/**
		 * Whether to scan for API keys and Bearer tokens (default: {@code true}).
		 */
		private boolean apiKeyEnabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isSsnEnabled() {
			return this.ssnEnabled;
		}

		public void setSsnEnabled(boolean ssnEnabled) {
			this.ssnEnabled = ssnEnabled;
		}

		public boolean isCreditCardEnabled() {
			return this.creditCardEnabled;
		}

		public void setCreditCardEnabled(boolean creditCardEnabled) {
			this.creditCardEnabled = creditCardEnabled;
		}

		public boolean isEmailEnabled() {
			return this.emailEnabled;
		}

		public void setEmailEnabled(boolean emailEnabled) {
			this.emailEnabled = emailEnabled;
		}

		public boolean isApiKeyEnabled() {
			return this.apiKeyEnabled;
		}

		public void setApiKeyEnabled(boolean apiKeyEnabled) {
			this.apiKeyEnabled = apiKeyEnabled;
		}

	}

	/**
	 * Session token budget sub-properties.
	 */
	public static class Budget {

		/**
		 * Whether budget tracking and enforcement is enabled (default: {@code true}).
		 */
		private boolean enabled = true;

		/**
		 * Maximum number of tokens allowed per session. A value of
		 * {@link SessionCostTracker#UNLIMITED} (zero or negative) means no limit is
		 * enforced (default: no limit).
		 */
		private long tokenLimit = SessionCostTracker.UNLIMITED;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public long getTokenLimit() {
			return this.tokenLimit;
		}

		public void setTokenLimit(long tokenLimit) {
			this.tokenLimit = tokenLimit;
		}

	}

}
