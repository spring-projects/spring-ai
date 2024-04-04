/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(SpringAiRetryProperties.CONFIG_PREFIX)
public class SpringAiRetryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.retry";

	/**
	 * Maximum number of retry attempts.
	 */
	private int maxAttempts = 10;

	/**
	 * Exponential Backoff properties.
	 */
	@NestedConfigurationProperty
	private Backoff backoff = new Backoff();

	/**
	 * If false, throw a NonTransientAiException, and do not attempt retry for 4xx client
	 * error codes. False by default. If true, throw a TransientAiException, and attempt
	 * retry for 4xx client.
	 */
	private boolean onClientErrors = false;

	/**
	 * List of HTTP status codes that should not trigger a retry (e.g. throw
	 * NonTransientAiException).
	 */
	private List<Integer> excludeOnHttpCodes = new ArrayList<>();

	/**
	 * List of HTTP status codes that should trigger a retry.
	 */
	private List<Integer> onHttpCodes = new ArrayList<>();

	/**
	 * Exponential Backoff properties.
	 */
	public static class Backoff {

		/**
		 * Initial sleep duration.
		 */
		private Duration initialInterval = Duration.ofMillis(2000);

		/**
		 * Backoff interval multiplier.
		 */
		private int multiplier = 5;

		/**
		 * Maximum backoff duration.
		 */
		private Duration maxInterval = Duration.ofMillis(3 * 60000);

		public Duration getInitialInterval() {
			return initialInterval;
		}

		public void setInitialInterval(Duration initialInterval) {
			this.initialInterval = initialInterval;
		}

		public int getMultiplier() {
			return multiplier;
		}

		public void setMultiplier(int multiplier) {
			this.multiplier = multiplier;
		}

		public Duration getMaxInterval() {
			return maxInterval;
		}

		public void setMaxInterval(Duration maxInterval) {
			this.maxInterval = maxInterval;
		}

	}

	public int getMaxAttempts() {
		return this.maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public Backoff getBackoff() {
		return this.backoff;
	}

	public List<Integer> getExcludeOnHttpCodes() {
		return this.excludeOnHttpCodes;
	}

	public void setExcludeOnHttpCodes(List<Integer> onHttpCodes) {
		this.excludeOnHttpCodes = onHttpCodes;
	}

	public boolean isOnClientErrors() {
		return this.onClientErrors;
	}

	public void setOnClientErrors(boolean onClientErrors) {
		this.onClientErrors = onClientErrors;
	}

	public List<Integer> getOnHttpCodes() {
		return this.onHttpCodes;
	}

	public void setOnHttpCodes(List<Integer> onHttpCodes) {
		this.onHttpCodes = onHttpCodes;
	}

}
