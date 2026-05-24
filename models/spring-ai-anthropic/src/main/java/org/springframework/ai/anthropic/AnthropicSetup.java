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

package org.springframework.ai.anthropic;

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
 * Factory class for creating and configuring Anthropic SDK client instances.
 *
 * <p>
 * This utility class provides static factory methods for creating both synchronous
 * ({@link AnthropicClient}) and asynchronous ({@link AnthropicClientAsync}) clients with
 * comprehensive configuration support. It provides sensible defaults for timeouts and
 * retry behavior.
 *
 * <p>
 * <b>Client Types:</b>
 * <ul>
 * <li><b>Synchronous Client:</b> Used for blocking API calls via
 * {@link #setupSyncClient}</li>
 * <li><b>Asynchronous Client:</b> Used for streaming responses via
 * {@link #setupAsyncClient}</li>
 * </ul>
 *
 * <p>
 * <b>Default Configuration:</b>
 * <ul>
 * <li>Timeout: 60 seconds</li>
 * <li>Max Retries: 2</li>
 * <li>User-Agent: {@code spring-ai-anthropic-sdk}</li>
 * </ul>
 *
 * <p>
 * This class is not intended to be instantiated directly. Use the static factory methods
 * to create client instances.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 2.0.0
 * @see org.springframework.ai.anthropic.AnthropicChatModel
 */
public final class AnthropicSetup {

	static final String ANTHROPIC_URL = "https://api.anthropic.com";

	static final String DEFAULT_USER_AGENT = "spring-ai-anthropic-sdk";

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSetup.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

	private static final int DEFAULT_MAX_RETRIES = 2;

	private AnthropicSetup() {
	}

	/**
	 * Creates a synchronous Anthropic client with the specified configuration.
	 * @param baseUrl the base URL for the API (null to use default)
	 * @param apiKey the API key
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured Anthropic client
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {

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

		if (apiKey != null) {
			builder.apiKey(apiKey);
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
	 * @param baseUrl the base URL for the API (null to use default)
	 * @param apiKey the API key
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured async Anthropic client
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {

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

		if (apiKey != null) {
			builder.apiKey(apiKey);
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

}
