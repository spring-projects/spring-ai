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

package org.springframework.ai.systemone.api;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.exception.SystemOneApiConnectionException;
import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneRateLimitException;
import org.springframework.util.Assert;

/**
 * How the client reacts to a transient failure. The defaults mirror the official SDKs of
 * the hosted service: two retries after the initial attempt, exponential backoff from
 * 500ms up to 5s with 25% jitter subtracted, and a 30 second budget for the whole call
 * including waits.
 *
 * <p>
 * Backing off on a rate limit is not optional: the API answers 429 with a
 * {@code retry-after-ms} header, and {@link #respectRetryAfter()} makes the client wait
 * exactly that long instead of guessing.
 *
 * @param maxRetries retries after the initial attempt; {@code 0} disables retrying
 * @param initialBackoff the first delay, doubled on each subsequent attempt
 * @param maxBackoff the upper bound on a single delay
 * @param jitter the fraction of each delay randomly subtracted, between {@code 0} and
 * {@code 1}
 * @param retryableStatuses the HTTP status codes worth retrying
 * @param respectRetryAfter whether a server stated wait overrides the computed backoff
 * @param retryConnectionErrors whether failures without an HTTP response are retried
 * @param totalTimeout the budget for the whole call including waits, or {@code null} for
 * none
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SystemOneRetryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff, double jitter,
		Set<Integer> retryableStatuses, boolean respectRetryAfter, boolean retryConnectionErrors,
		@Nullable Duration totalTimeout) {

	/**
	 * Status codes explicitly retried on top of every 5xx, which is always retried:
	 * request timeout and rate limit.
	 */
	public static final Set<Integer> DEFAULT_RETRYABLE_STATUSES = Set.of(408, 429);

	public SystemOneRetryPolicy {
		Assert.isTrue(maxRetries >= 0, "maxRetries must not be negative");
		Assert.notNull(initialBackoff, "initialBackoff must not be null");
		Assert.notNull(maxBackoff, "maxBackoff must not be null");
		Assert.isTrue(!initialBackoff.isNegative(), "initialBackoff must not be negative");
		Assert.isTrue(!maxBackoff.isNegative(), "maxBackoff must not be negative");
		Assert.isTrue(jitter >= 0.0d && jitter <= 1.0d, "jitter must be between 0 and 1");
		Assert.notNull(retryableStatuses, "retryableStatuses must not be null");
		retryableStatuses = Set.copyOf(retryableStatuses);
	}

	/**
	 * @return the policy used when none is configured
	 */
	public static SystemOneRetryPolicy defaults() {
		return builder().build();
	}

	/**
	 * @return a policy that fails on the first error
	 */
	public static SystemOneRetryPolicy noRetry() {
		return builder().maxRetries(0).build();
	}

	/**
	 * Decides whether a failed attempt is worth repeating.
	 * @param throwable the failure of the attempt
	 * @return {@code true} when another attempt may succeed
	 */
	public boolean isRetryable(Throwable throwable) {
		if (this.maxRetries == 0) {
			return false;
		}
		if (throwable instanceof SystemOneApiException apiException) {
			int status = apiException.status();
			return this.retryableStatuses.contains(status) || status >= 500;
		}
		return this.retryConnectionErrors && throwable instanceof SystemOneApiConnectionException;
	}

	/**
	 * Computes how long to wait before the given attempt.
	 * @param attempt the one-based number of the attempt that just failed
	 * @param throwable the failure of that attempt
	 * @return the delay before the next attempt, never negative
	 */
	public Duration backoffFor(int attempt, Throwable throwable) {
		if (this.respectRetryAfter && throwable instanceof SystemOneRateLimitException rateLimit) {
			Duration retryAfter = rateLimit.retryAfter();
			if (retryAfter != null) {
				return retryAfter;
			}
		}
		// Exponential: initial, initial*2, initial*4 ... capped at maxBackoff.
		long millis = this.initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 30));
		millis = Math.min(millis, this.maxBackoff.toMillis());
		if (this.jitter > 0.0d && millis > 0) {
			double factor = 1.0d - ThreadLocalRandom.current().nextDouble() * this.jitter;
			millis = (long) (millis * factor);
		}
		return Duration.ofMillis(Math.max(millis, 0));
	}

	/**
	 * @return a new builder pre-loaded with the default policy
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SystemOneRetryPolicy}.
	 */
	public static final class Builder {

		private int maxRetries = 2;

		private Duration initialBackoff = Duration.ofMillis(500);

		private Duration maxBackoff = Duration.ofSeconds(5);

		private double jitter = 0.25d;

		private Set<Integer> retryableStatuses = DEFAULT_RETRYABLE_STATUSES;

		private boolean respectRetryAfter = true;

		private boolean retryConnectionErrors = true;

		private @Nullable Duration totalTimeout = Duration.ofSeconds(30);

		private Builder() {
		}

		public Builder maxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public Builder initialBackoff(Duration initialBackoff) {
			this.initialBackoff = initialBackoff;
			return this;
		}

		public Builder maxBackoff(Duration maxBackoff) {
			this.maxBackoff = maxBackoff;
			return this;
		}

		public Builder jitter(double jitter) {
			this.jitter = jitter;
			return this;
		}

		public Builder retryableStatuses(Set<Integer> retryableStatuses) {
			this.retryableStatuses = retryableStatuses;
			return this;
		}

		public Builder respectRetryAfter(boolean respectRetryAfter) {
			this.respectRetryAfter = respectRetryAfter;
			return this;
		}

		public Builder retryConnectionErrors(boolean retryConnectionErrors) {
			this.retryConnectionErrors = retryConnectionErrors;
			return this;
		}

		public Builder totalTimeout(@Nullable Duration totalTimeout) {
			this.totalTimeout = totalTimeout;
			return this;
		}

		public SystemOneRetryPolicy build() {
			return new SystemOneRetryPolicy(this.maxRetries, this.initialBackoff, this.maxBackoff, this.jitter,
					this.retryableStatuses, this.respectRetryAfter, this.retryConnectionErrors, this.totalTimeout);
		}

	}
}
