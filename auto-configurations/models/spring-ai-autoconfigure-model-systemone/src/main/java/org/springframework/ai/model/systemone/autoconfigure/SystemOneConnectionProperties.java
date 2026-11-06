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

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.SystemOneApiConstants;
import org.springframework.ai.systemone.api.SystemOneRetryUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.retry.RetryPolicy;

/**
 * Connection properties of the System One client: where the server is, how to
 * authenticate, how long an attempt may take and how failures are retried.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@ConfigurationProperties(SystemOneConnectionProperties.CONFIG_PREFIX)
public class SystemOneConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.systemone";

	/**
	 * The API key. Required.
	 */
	private @Nullable String apiKey;

	/**
	 * The API root; the hosted System One service by default. Point it at a Laya, llmman
	 * or other server of the protocol.
	 */
	private String baseUrl = SystemOneApiConstants.DEFAULT_BASE_URL;

	/**
	 * The read timeout of each attempt. Unset, the HTTP client settings of the
	 * application apply, or 10s when the client builds its own transport.
	 */
	private @Nullable Duration timeout;

	private final Retry retry = new Retry();

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public Retry getRetry() {
		return this.retry;
	}

	/**
	 * How transient failures are retried. The defaults are those of
	 * {@link SystemOneRetryUtils#DEFAULT_RETRY_POLICY}.
	 */
	public static class Retry {

		/**
		 * Retries after the initial attempt; {@code 0} disables retrying.
		 */
		private int maxRetries = SystemOneRetryUtils.DEFAULT_MAX_RETRIES;

		/**
		 * The first wait, doubled on each further retry.
		 */
		private Duration initialBackoff = SystemOneRetryUtils.DEFAULT_INITIAL_BACKOFF;

		/**
		 * The upper bound on a single wait.
		 */
		private Duration maxBackoff = SystemOneRetryUtils.DEFAULT_MAX_BACKOFF;

		/**
		 * No further attempt is started once this much time has passed since the first.
		 */
		private Duration timeout = SystemOneRetryUtils.DEFAULT_TIMEOUT;

		public int getMaxRetries() {
			return this.maxRetries;
		}

		public void setMaxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
		}

		public Duration getInitialBackoff() {
			return this.initialBackoff;
		}

		public void setInitialBackoff(Duration initialBackoff) {
			this.initialBackoff = initialBackoff;
		}

		public Duration getMaxBackoff() {
			return this.maxBackoff;
		}

		public void setMaxBackoff(Duration maxBackoff) {
			this.maxBackoff = maxBackoff;
		}

		public Duration getTimeout() {
			return this.timeout;
		}

		public void setTimeout(Duration timeout) {
			this.timeout = timeout;
		}

		/**
		 * The policy these settings describe, built by
		 * {@link SystemOneRetryUtils#retryPolicy}.
		 * @return the policy
		 * @throws IllegalStateException when the settings cannot work, naming the
		 * property
		 */
		public RetryPolicy toRetryPolicy() {
			try {
				return SystemOneRetryUtils.retryPolicy(this.maxRetries, this.initialBackoff, this.maxBackoff,
						this.timeout);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException(CONFIG_PREFIX + ".retry: " + ex.getMessage());
			}
		}

	}

}
