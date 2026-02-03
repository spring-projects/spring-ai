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

package org.springframework.ai.anthropicsdk;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Base class for common Anthropic SDK configuration options, extended by
 * {@link AnthropicSdkChatOptions}.
 *
 * <p>
 * Supports environment variables {@code ANTHROPIC_API_KEY} and {@code ANTHROPIC_BASE_URL}
 * for configuration.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see AnthropicSdkChatOptions
 */
public class AbstractAnthropicSdkOptions {

	/**
	 * The base URL to connect to the Anthropic API. Defaults to
	 * "https://api.anthropic.com" if not specified.
	 */
	private @Nullable String baseUrl;

	/**
	 * The API key to authenticate with the Anthropic API. Can also be set via the
	 * ANTHROPIC_API_KEY environment variable.
	 */
	private @Nullable String apiKey;

	/**
	 * The model name to use for requests.
	 */
	private @Nullable String model;

	/**
	 * Request timeout for the Anthropic client. Defaults to 60 seconds if not specified.
	 */
	private @Nullable Duration timeout;

	/**
	 * Maximum number of retries for failed requests. Defaults to 2 if not specified.
	 */
	private @Nullable Integer maxRetries;

	/**
	 * Proxy settings for the Anthropic client.
	 */
	private @Nullable Proxy proxy;

	/**
	 * Custom HTTP headers to add to Anthropic client requests.
	 */
	private Map<String, String> customHeaders = new HashMap<>();

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(@Nullable String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public @Nullable Integer getMaxRetries() {
		return this.maxRetries;
	}

	public void setMaxRetries(@Nullable Integer maxRetries) {
		this.maxRetries = maxRetries;
	}

	public @Nullable Proxy getProxy() {
		return this.proxy;
	}

	public void setProxy(@Nullable Proxy proxy) {
		this.proxy = proxy;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	public void setCustomHeaders(Map<String, String> customHeaders) {
		this.customHeaders = customHeaders;
	}

}
