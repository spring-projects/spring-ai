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

package org.springframework.ai.anthropicsdk.setup;

import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps configure the Anthropic Java SDK client. Provides factory methods for creating
 * sync and async clients with various configuration options.
 *
 * @author Soby Chacko
 */
public final class AnthropicSdkSetup {

	static final String ANTHROPIC_URL = "https://api.anthropic.com";

	static final String ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

	static final String ANTHROPIC_AUTH_TOKEN = "ANTHROPIC_AUTH_TOKEN";

	static final String ANTHROPIC_BASE_URL = "ANTHROPIC_BASE_URL";

	static final String DEFAULT_USER_AGENT = "spring-ai-anthropic-sdk";

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSdkSetup.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

	private static final int DEFAULT_MAX_RETRIES = 2;

	private AnthropicSdkSetup() {
	}

	/**
	 * Creates a synchronous Anthropic client with the specified configuration.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured Anthropic client
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);

		if (timeout == null) {
			timeout = DEFAULT_TIMEOUT;
		}
		if (maxRetries == null) {
			maxRetries = DEFAULT_MAX_RETRIES;
		}

		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();

		if (baseUrl != null) {
			builder.baseUrl(baseUrl);
		}

		String resolvedApiKey = apiKey != null ? apiKey : detectApiKey();
		if (resolvedApiKey != null) {
			builder.apiKey(resolvedApiKey);
		}

		if (proxy != null) {
			builder.proxy(proxy);
		}

		builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
		if (customHeaders != null) {
			builder.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		builder.timeout(timeout);
		builder.maxRetries(maxRetries);

		return builder.build();
	}

	/**
	 * Creates an asynchronous Anthropic client with the specified configuration. The
	 * async client is used for streaming responses.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured async Anthropic client
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);

		if (timeout == null) {
			timeout = DEFAULT_TIMEOUT;
		}
		if (maxRetries == null) {
			maxRetries = DEFAULT_MAX_RETRIES;
		}

		AnthropicOkHttpClientAsync.Builder builder = AnthropicOkHttpClientAsync.builder();

		if (baseUrl != null) {
			builder.baseUrl(baseUrl);
		}

		String resolvedApiKey = apiKey != null ? apiKey : detectApiKey();
		if (resolvedApiKey != null) {
			builder.apiKey(resolvedApiKey);
		}

		if (proxy != null) {
			builder.proxy(proxy);
		}

		builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
		if (customHeaders != null) {
			builder.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		builder.timeout(timeout);
		builder.maxRetries(maxRetries);

		return builder.build();
	}

	/**
	 * Detects the base URL from environment variable if not explicitly provided.
	 * @param baseUrl the explicitly provided base URL (may be null)
	 * @return the base URL to use
	 */
	static @Nullable String detectBaseUrlFromEnv(@Nullable String baseUrl) {
		if (baseUrl == null) {
			String envBaseUrl = System.getenv(ANTHROPIC_BASE_URL);
			if (envBaseUrl != null) {
				logger.debug("Anthropic Base URL detected from environment variable {}.", ANTHROPIC_BASE_URL);
				return envBaseUrl;
			}
		}
		return baseUrl;
	}

	/**
	 * Detects the API key from environment variables.
	 * @return the API key, or null if not found
	 */
	static @Nullable String detectApiKey() {
		String apiKey = System.getenv(ANTHROPIC_API_KEY);
		if (apiKey != null) {
			logger.debug("Anthropic API key detected from environment variable {}.", ANTHROPIC_API_KEY);
			return apiKey;
		}

		String authToken = System.getenv(ANTHROPIC_AUTH_TOKEN);
		if (authToken != null) {
			logger.debug("Anthropic auth token detected from environment variable {}.", ANTHROPIC_AUTH_TOKEN);
			return authToken;
		}

		return null;
	}

}
