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

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
	 * Anthropic API version.
	 */
	private String version = AnthropicApi.DEFAULT_ANTHROPIC_VERSION;

	/**
	 * Beta features version. Such as tools-2024-04-04 or
	 * max-tokens-3-5-sonnet-2024-07-15.
	 */
	private String betaVersion = AnthropicApi.DEFAULT_ANTHROPIC_BETA_VERSION;

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

}
