/*
 * Copyright 2023-2024 the original author or authors.
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogAccessor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * RetryUtils is a utility class for configuring and handling retry operations. It
 * provides a default RetryTemplate and a default ResponseErrorHandler.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public abstract class RetryUtils {

	private static final LogAccessor logger = new LogAccessor(LogFactory.getLog(RetryUtils.class));

	public static final ResponseErrorHandler DEFAULT_RESPONSE_ERROR_HANDLER = new ResponseErrorHandler() {

		@Override
		public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
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

	public static final RetryTemplate DEFAULT_RETRY_TEMPLATE = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(TransientAiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {

			@Override
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn(throwable, "Retry error. Retry count:" + context.getRetryCount());
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
