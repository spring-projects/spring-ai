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
 * Base class providing common configuration options for Anthropic SDK clients. This class
 * contains settings that are shared across different Anthropic model types (chat,
 * embedding, etc.).
 *
 * @author Soby Chacko
 */
public class AbstractAnthropicSdkOptions {

	/**
	 * The base URL to connect to the Anthropic API. Defaults to
	 * "https://api.anthropic.com" if not specified.
	 */
	@Nullable private String baseUrl;

	/**
	 * The API key to authenticate with the Anthropic API. Can also be set via the
	 * ANTHROPIC_API_KEY environment variable.
	 */
	@Nullable private String apiKey;

	/**
	 * The model name to use for requests.
	 */
	@Nullable private String model;

	/**
	 * Request timeout for the Anthropic client. Defaults to 60 seconds if not specified.
	 */
	@Nullable private Duration timeout;

	/**
	 * Maximum number of retries for failed requests. Defaults to 2 if not specified.
	 */
	@Nullable private Integer maxRetries;

	/**
	 * Proxy settings for the Anthropic client.
	 */
	@Nullable private Proxy proxy;

	/**
	 * Custom HTTP headers to add to Anthropic client requests.
	 */
	private Map<String, String> customHeaders = new HashMap<>();

	@Nullable public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(@Nullable String baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Nullable public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	@Nullable public String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Nullable public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	@Nullable public Integer getMaxRetries() {
		return this.maxRetries;
	}

	public void setMaxRetries(@Nullable Integer maxRetries) {
		this.maxRetries = maxRetries;
	}

	@Nullable public Proxy getProxy() {
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
