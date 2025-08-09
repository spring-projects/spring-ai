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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * RetryUtils is a utility class for configuring and handling retry operations. It
 * provides a default RetryTemplate and a default ResponseErrorHandler.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 0.8.1
 */
public abstract class RetryUtils {

	public static final ResponseErrorHandler DEFAULT_RESPONSE_ERROR_HANDLER = new ResponseErrorHandler() {

		@Override
		public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
		public void handleError(URI url, HttpMethod method, @NonNull ClientHttpResponse response) throws IOException {
			handleError(response);
		}

		@SuppressWarnings("removal")
		public void handleError(@NonNull ClientHttpResponse response) throws IOException {
			if (response.getStatusCode().isError()) {
				String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
				String message = String.format("%s - %s", response.getStatusCode().value(), error);
				/**
				 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided,
				 * 401 - You must be a member of an organization to use the API, 429 -
				 * Rate limit reached for requests, 429 - You exceeded your current quota
				 * , please check your plan and billing details.
				 */
				if (response.getStatusCode().is4xxClientError()) {
					throw new NonTransientAiException(message);
				}
				throw new TransientAiException(message);
			}
		}
	};

	private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

	public static final RetryTemplate DEFAULT_RETRY_TEMPLATE = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(TransientAiException.class)
		.retryOn(ResourceAccessException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {

			@Override
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			}
		})
		.build();

	/**
	 * Useful in testing scenarios where you don't want to wait long for retry and now
	 * show stack trace
	 */
	public static final RetryTemplate SHORT_RETRY_TEMPLATE = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(TransientAiException.class)
		.retryOn(ResourceAccessException.class)
		.fixedBackoff(Duration.ofMillis(100))
		.withListener(new RetryListener() {

			@Override
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount());
			}
		})
		.build();

}
