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

	/**
	 * The default policy: two retries, exponential backoff from 500ms doubling up to 5s
	 * with jitter, no further attempt once 30 seconds have passed since the first, and
	 * the server stated wait on a rate limit. With two retries the waits are 500ms and
	 * 1s; the 5s cap only matters for a policy with more. The 30 seconds are measured
	 * from the first attempt, so an attempt already running when they pass still
	 * completes.
	 */
	public static final RetryPolicy DEFAULT_RETRY_POLICY = honouringRetryAfter(RetryPolicy.builder()
		.maxRetries(2)
		.delay(Duration.ofMillis(500))
		.multiplier(2.0d)
		.maxDelay(Duration.ofSeconds(5))
		.jitter(Duration.ofMillis(100))
		.timeout(Duration.ofSeconds(30))
		.predicate(SystemOneRetryUtils::isTransient)
		.build());

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
	 * A new template over {@link #DEFAULT_RETRY_POLICY} that logs each retry at warn
	 * level, used by the client when none is configured. A new instance each time, since
	 * a template is mutable.
	 * @return the template
	 */
	public static RetryTemplate defaultRetryTemplate() {
		RetryTemplate template = new RetryTemplate(DEFAULT_RETRY_POLICY);
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
			return () -> {
				BackOffExecution execution = backOff.start();
				return () -> {
					long next = execution.nextBackOff();
					Long serverWait = SERVER_WAIT_MS.get();
					SERVER_WAIT_MS.remove();
					return (next == BackOffExecution.STOP || serverWait == null) ? next : serverWait;
				};
			};
		}

	}

}
