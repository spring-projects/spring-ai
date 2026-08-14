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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic connection properties.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 2.0.0
 */
@ConfigurationProperties(AnthropicConnectionProperties.CONFIG_PREFIX)
public class AnthropicConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic";

	private @Nullable String baseUrl;

	private @Nullable String apiKey;

	private @Nullable Duration timeout;

	private @Nullable Integer maxRetries;

	private @Nullable Proxy proxy;

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
