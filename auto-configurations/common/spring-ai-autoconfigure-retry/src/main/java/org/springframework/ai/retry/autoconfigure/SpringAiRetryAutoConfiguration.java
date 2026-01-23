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

package org.springframework.ai.retry.autoconfigure;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * {@link AutoConfiguration Auto-configuration} for AI Retry. Provides beans for retry
 * template and response error handling. Handles transient and non-transient exceptions
 * based on HTTP status codes.
 *
 * @author Christian Tzolov
 * @author SriVarshan P
 * @author Seunggyu Lee
 */
@AutoConfiguration
@ConditionalOnClass(RetryUtils.class)
@EnableConfigurationProperties({ SpringAiRetryProperties.class })
public class SpringAiRetryAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiRetryAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public RetryTemplate retryTemplate(SpringAiRetryProperties properties) {
		RetryPolicy retryPolicy = RetryPolicy.builder()
			.maxRetries(properties.getMaxAttempts())
			.includes(TransientAiException.class)
			.includes(ResourceAccessException.class)
			.delay(properties.getBackoff().getInitialInterval())
			.multiplier(properties.getBackoff().getMultiplier())
			.maxDelay(properties.getBackoff().getMaxInterval())
			.build();

		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(new RetryListener() {
			private final AtomicInteger retryCount = new AtomicInteger(0);

			@Override
			public void onRetryFailure(RetryPolicy policy, Retryable<?> retryable, Throwable throwable) {
				int currentRetries = this.retryCount.incrementAndGet();
				logger.warn("Retry error. Retry count:{}", currentRetries, throwable);
			}
		});
		return retryTemplate;
	}

	@Bean
	@ConditionalOnMissingBean
	public ResponseErrorHandler responseErrorHandler(SpringAiRetryProperties properties) {

		return new ResponseErrorHandler() {

			@Override
			public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(@NonNull URI url, @NonNull HttpMethod method, @NonNull ClientHttpResponse response)
					throws IOException {
				handleError(response);
			}

			@SuppressWarnings("removal")
			public void handleError(@NonNull ClientHttpResponse response) throws IOException {
				if (!response.getStatusCode().isError()) {
					return;
				}

				String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
				if (error == null || error.isEmpty()) {
					error = "No response body available";
				}

				String message = String.format("HTTP %s - %s", response.getStatusCode().value(), error);

				// Explicitly configured transient codes
				if (properties.getOnHttpCodes().contains(response.getStatusCode().value())) {
					throw new TransientAiException(message);
				}

				// Handle client errors (4xx)
				if (!properties.isOnClientErrors() && response.getStatusCode().is4xxClientError()) {
					throw new NonTransientAiException(message);
				}

				// Explicitly configured non-transient codes
				if (!CollectionUtils.isEmpty(properties.getExcludeOnHttpCodes())
						&& properties.getExcludeOnHttpCodes().contains(response.getStatusCode().value())) {
					throw new NonTransientAiException(message);
				}

				// Default to transient exception
				throw new TransientAiException(message);
			}
		};
	}

}
