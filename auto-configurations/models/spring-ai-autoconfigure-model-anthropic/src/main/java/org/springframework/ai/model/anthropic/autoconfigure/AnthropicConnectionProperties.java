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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.time.Duration;

import jakarta.annotation.Nullable;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsProperties;

/**
 * Anthropic API connection properties.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(AnthropicConnectionProperties.CONFIG_PREFIX)
public class AnthropicConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic";

	/**
	 * Anthropic API access key.
	 */
	private String apiKey;

	/**
	 * Anthropic API base URL.
	 */
	private String baseUrl = AnthropicApi.DEFAULT_BASE_URL;

	/**
	 * Path to append to the base URL
	 */
	private String completionsPath = AnthropicApi.DEFAULT_MESSAGE_COMPLETIONS_PATH;

	/**
	 * Anthropic API version.
	 */
	private String version = AnthropicApi.DEFAULT_ANTHROPIC_VERSION;

	/**
	 * Beta features version. Such as tools-2024-04-04 or
	 * max-tokens-3-5-sonnet-2024-07-15.
	 */
	private String betaVersion = AnthropicApi.DEFAULT_ANTHROPIC_BETA_VERSION;

	@NestedConfigurationProperty
	private final HttpClientSettingsProperties http = new HttpClientSettingsProperties() {
	};

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getCompletionsPath() {
		return this.completionsPath;
	}

	public void setCompletionsPath(String completionsPath) {
		this.completionsPath = completionsPath;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getBetaVersion() {
		return this.betaVersion;
	}

	public void setBetaVersion(String betaVersion) {
		this.betaVersion = betaVersion;
	}

	@Nullable
	public HttpRedirects getRedirects() {
		return this.http.getRedirects();
	}

	public void setRedirects(HttpRedirects redirects) {
		this.http.setRedirects(redirects);
	}

	@Nullable
	public Duration getConnectTimeout() {
		return this.http.getConnectTimeout();
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.http.setConnectTimeout(connectTimeout);
	}

	@Nullable
	public Duration getReadTimeout() {
		return this.http.getReadTimeout();
	}

	public void setReadTimeout(Duration readTimeout) {
		this.http.setReadTimeout(readTimeout);
	}

	public HttpClientSettingsProperties.Ssl getSsl() {
		return this.http.getSsl();
	}

	public HttpClientSettingsProperties getHttp() {
		return this.http;
	}

}
