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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.exception.SystemOneApiConnectionException;
import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneRateLimitException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.Assert;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * The retry defaults of the {@link SystemOneClient}, built on the Spring Framework
 * {@link RetryTemplate}. The defaults mirror the official SDKs of the hosted service: two
 * retries after the initial attempt, waiting 500ms and then 1s with some jitter, no
 * further attempt once 30 seconds have passed since the first, and the wait the server
 * states on a rate limit in place of the computed one.
 *
 * <p>
 * A policy of your own keeps the typed exceptions retryable by classifying with
 * {@link #isTransient(Throwable)}, and keeps the server stated wait through
 * {@link #honouringRetryAfter(RetryPolicy)}:
 *
 * <pre>{@code
 * RetryPolicy policy = SystemOneRetryUtils.honouringRetryAfter(RetryPolicy.builder()
 *     .maxRetries(5)
 *     .delay(Duration.ofSeconds(1))
 *     .predicate(SystemOneRetryUtils::isTransient)
 *     .build());
 * SystemOneClient client = SystemOneClient.builder()
 *     .apiKey(apiKey)
 *     .retryTemplate(new RetryTemplate(policy))
 *     .build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SystemOneRetryUtils {

	private static final Log logger = LogFactory.getLog(SystemOneRetryUtils.class);

	/**
	 * Status codes retried on top of every 5xx, which is always retried: request timeout
	 * and rate limit.
	 */
	public static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429);

	/** The default number of retries after the initial attempt. */
	public static final int DEFAULT_MAX_RETRIES = 2;

	/** The default first wait, doubled on each further retry. */
	public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);

	/** The default upper bound on a single wait. */
	public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(5);

	/** The default time since the first attempt after which no further attempt starts. */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * The default policy, built by {@link #retryPolicy} from the defaults above: two
	 * retries, waiting 500ms and then 1s with jitter, no further attempt once 30 seconds
	 * have passed since the first, and the server stated wait on a rate limit. The 5s cap
	 * only matters for a policy with more retries. The 30 seconds are measured from the
	 * first attempt, so an attempt already running when they pass still completes.
	 */
	public static final RetryPolicy DEFAULT_RETRY_POLICY = retryPolicy(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF,
			DEFAULT_MAX_BACKOFF, DEFAULT_TIMEOUT);

	/**
	 * A policy that fails on the first error.
	 */
	public static final RetryPolicy NO_RETRY_POLICY = RetryPolicy.builder()
		.maxRetries(0)
		.predicate(SystemOneRetryUtils::isTransient)
		.build();

	private SystemOneRetryUtils() {
	}

	/**
	 * A policy with the System One classification and the server stated wait on a rate
	 * limit: exponential waits doubling from the first up to the cap, with a fifth of the
	 * first wait of jitter up to 100ms, and no further attempt once the timeout has
	 * passed since the first.
	 * @param maxRetries retries after the initial attempt; {@code 0} disables retrying
	 * @param initialBackoff the first wait
	 * @param maxBackoff the upper bound on a single wait, at least the first
	 * @param timeout the time since the first attempt after which no further attempt
	 * starts
	 * @return the policy
	 */
	public static RetryPolicy retryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff,
			Duration timeout) {
		Assert.isTrue(maxRetries >= 0, "maxRetries must not be negative");
		Assert.notNull(initialBackoff, "initialBackoff must not be null");
		Assert.notNull(maxBackoff, "maxBackoff must not be null");
		Assert.notNull(timeout, "timeout must not be null");
		Assert.isTrue(!initialBackoff.isNegative(), "initialBackoff must not be negative");
		Assert.isTrue(maxBackoff.compareTo(initialBackoff) >= 0 && !maxBackoff.isZero(),
				"maxBackoff must be positive and at least initialBackoff");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
		return honouringRetryAfter(RetryPolicy.builder()
			.maxRetries(maxRetries)
			.delay(initialBackoff)
			.multiplier(2.0d)
			.maxDelay(maxBackoff)
			.jitter(Duration.ofMillis(Math.min(100, initialBackoff.toMillis() / 5)))
			.timeout(timeout)
			.predicate(SystemOneRetryUtils::isTransient)
			.build());
	}

	/**
	 * A new template over {@link #DEFAULT_RETRY_POLICY} that logs each retry at warn
	 * level, used by the client when none is configured. A new instance each time, since
	 * a template is mutable.
	 * @return the template
	 */
	public static RetryTemplate defaultRetryTemplate() {
		return retryTemplate(DEFAULT_RETRY_POLICY);
	}

	/**
	 * A new template over the given policy that logs each retry at warn level.
	 * @param policy the policy
	 * @return the template
	 */
	public static RetryTemplate retryTemplate(RetryPolicy policy) {
		Assert.notNull(policy, "policy must not be null");
		RetryTemplate template = new RetryTemplate(policy);
		template.setRetryListener(new RetryListener() {

			@Override
			public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
				if (logger.isWarnEnabled()) {
					logger.warn("%s failed (%s), retry %d".formatted(retryable.getName(),
							retryState.getLastException().getMessage(), retryState.getRetryCount()));
				}
			}

		});
		return template;
	}

	/**
	 * A new template that fails on the first error.
	 * @return the template
	 */
	public static RetryTemplate noRetryTemplate() {
		return new RetryTemplate(NO_RETRY_POLICY);
	}

	/**
	 * Decides whether a failed attempt is worth repeating: a failure without an HTTP
	 * response, a request timeout, a rate limit or a server error. Anything else, such as
	 * a rejected key or a malformed request, fails the same way again.
	 * @param throwable the failure of the attempt
	 * @return {@code true} when another attempt may succeed
	 */
	public static boolean isTransient(Throwable throwable) {
		if (throwable instanceof SystemOneApiException apiException) {
			int status = apiException.status();
			return RETRYABLE_STATUSES.contains(status) || status >= 500;
		}
		return throwable instanceof SystemOneApiConnectionException;
	}

	/**
	 * Wraps a policy so that the wait the server states on a rate limit, carried by
	 * {@link SystemOneRateLimitException#retryAfter()}, is used in place of the wait the
	 * policy computed. The API answers 429 with a {@code retry-after-ms} header, and
	 * retrying sooner only burns attempts.
	 *
	 * <p>
	 * A stated wait longer than the timeout of the policy is cut to that timeout. No
	 * attempt can follow such a wait, so the template gives up either way, and it does so
	 * for any value the server sends, however large.
	 * @param policy the policy to wrap
	 * @return the wrapped policy
	 */
	public static RetryPolicy honouringRetryAfter(RetryPolicy policy) {
		Assert.notNull(policy, "policy must not be null");
		return new RetryAfterPolicy(policy);
	}

	/**
	 * The template consults {@link #shouldRetry(Throwable)} and then the back-off, in
	 * that order and on the calling thread, so the wait read off the failure in the first
	 * is handed to the second through a thread-local. Every classification overwrites it
	 * before the back-off reads it, and the read clears it, so the back-off never sees
	 * the wait of an earlier failure.
	 */
	private static final class RetryAfterPolicy implements RetryPolicy {

		private static final ThreadLocal<@Nullable Long> SERVER_WAIT_MS = new ThreadLocal<>();

		private final RetryPolicy delegate;

		RetryAfterPolicy(RetryPolicy delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean shouldRetry(Throwable throwable) {
			boolean retry = this.delegate.shouldRetry(throwable);
			Duration retryAfter = (retry && throwable instanceof SystemOneRateLimitException rateLimit)
					? rateLimit.retryAfter() : null;
			if (retryAfter != null) {
				SERVER_WAIT_MS.set(retryAfter.toMillis());
			}
			else {
				SERVER_WAIT_MS.remove();
			}
			return retry;
		}

		@Override
		public Duration getTimeout() {
			return this.delegate.getTimeout();
		}

		@Override
		public BackOff getBackOff() {
			BackOff backOff = this.delegate.getBackOff();
			long timeout = this.delegate.getTimeout().toMillis();
			return () -> {
				BackOffExecution execution = backOff.start();
				return () -> {
					long next = execution.nextBackOff();
					Long serverWait = SERVER_WAIT_MS.get();
					SERVER_WAIT_MS.remove();
					if (next == BackOffExecution.STOP || serverWait == null) {
						return next;
					}
					// The template adds the wait to the clock to see whether it passes
					// the timeout, and a wait near Long.MAX_VALUE overflows that sum, so
					// the template would sleep instead of giving up. The timeout itself
					// is already too long a wait.
					return (timeout > 0) ? Math.min(serverWait, timeout) : serverWait;
				};
			};
		}

	}

}
