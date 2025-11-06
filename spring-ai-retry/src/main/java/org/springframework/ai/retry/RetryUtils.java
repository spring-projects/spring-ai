/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.retry;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * RetryUtils is a utility class for configuring and handling retry operations. It
 * provides a default RetryTemplate and a default ResponseErrorHandler.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Yanming Zhou
 * @since 0.8.1
 */
public abstract class RetryUtils {

	private static final int DEFAULT_MAX_ATTEMPTS = 10;

	private static final long DEFAULT_INITIAL_INTERVAL = 2000;

	private static final int DEFAULT_MULTIPLIER = 5;

	private static final long DEFAULT_MAX_INTERVAL = 3 * 60000;

	private static final long SHORT_INITIAL_INTERVAL = 100;

	private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtils.class);

	/**
	 * Default ResponseErrorHandler implementation.
	 */
	public static final ResponseErrorHandler DEFAULT_RESPONSE_ERROR_HANDLER = new ResponseErrorHandler() {

		@Override
		public boolean hasError(final ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
		public void handleError(final URI url, final HttpMethod method, final ClientHttpResponse response)
				throws IOException {
			handleError(response);
		}

		public void handleError(final ClientHttpResponse response) throws IOException {
			String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
			String message = String.format("%s - %s", response.getStatusCode().value(), error);
			/*
			 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided, 401
			 * - You must be a member of an organization to use the API, 429 - Rate limit
			 * reached for requests, 429 - You exceeded your current quota, please check
			 * your plan and billing details.
			 */
			if (response.getStatusCode().is4xxClientError()) {
				throw new NonTransientAiException(message);
			}
			throw new TransientAiException(message);
		}

	};

	/**
	 * Default RetryTemplate with exponential backoff configuration.
	 */
	public static final RetryTemplate DEFAULT_RETRY_TEMPLATE = createDefaultRetryTemplate();

	/**
	 * Short RetryTemplate for testing scenarios.
	 */
	public static final RetryTemplate SHORT_RETRY_TEMPLATE = createShortRetryTemplate();

	private static RetryTemplate createDefaultRetryTemplate() {
		RetryPolicy retryPolicy = RetryPolicy.builder()
			.maxRetries(DEFAULT_MAX_ATTEMPTS)
			.includes(TransientAiException.class)
			.includes(ResourceAccessException.class)
			.delay(Duration.ofMillis(DEFAULT_INITIAL_INTERVAL))
			.multiplier(DEFAULT_MULTIPLIER)
			.maxDelay(Duration.ofMillis(DEFAULT_MAX_INTERVAL))
			.build();

		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(new RetryListener() {
			private final AtomicInteger retryCount = new AtomicInteger(0);

			@Override
			public void onRetryFailure(final RetryPolicy policy, final Retryable<?> retryable,
					final Throwable throwable) {
				int currentRetries = this.retryCount.incrementAndGet();
				LOGGER.warn("Retry error. Retry count:{}", currentRetries, throwable);
			}
		});
		return retryTemplate;
	}

	/**
	 * Useful in testing scenarios where you don't want to wait long for retry and don't
	 * need to show stack trace.
	 * @return a RetryTemplate with short delays
	 */
	private static RetryTemplate createShortRetryTemplate() {
		RetryPolicy retryPolicy = RetryPolicy.builder()
			.maxRetries(DEFAULT_MAX_ATTEMPTS)
			.includes(TransientAiException.class)
			.includes(ResourceAccessException.class)
			.delay(Duration.ofMillis(SHORT_INITIAL_INTERVAL))
			.build();

		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(new RetryListener() {
			private final AtomicInteger retryCount = new AtomicInteger(0);

			@Override
			public void onRetryFailure(final RetryPolicy policy, final Retryable<?> retryable,
					final Throwable throwable) {
				int currentRetries = this.retryCount.incrementAndGet();
				LOGGER.warn("Retry error. Retry count:{}", currentRetries, throwable);
			}
		});
		return retryTemplate;
	}

	/**
	 * Generic execute method to run retryable operations with the provided RetryTemplate.
	 * @param <R> the return type
	 * @param retryTemplate the RetryTemplate to use for executing the retryable operation
	 * @param retryable the operation to be retried
	 * @return the result of the retryable operation
	 */
	public static <R extends @Nullable Object> R execute(RetryTemplate retryTemplate, Retryable<R> retryable) {
		try {
			return retryTemplate.execute(retryable);
		}
		catch (RetryException e) {
			throw (e.getCause() instanceof RuntimeException runtime) ? runtime
					: new RuntimeException(e.getMessage(), e.getCause());
		}
	}

}
