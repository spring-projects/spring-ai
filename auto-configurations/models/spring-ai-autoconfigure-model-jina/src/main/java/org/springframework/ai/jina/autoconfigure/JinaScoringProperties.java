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

package org.springframework.ai.jina.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.jina.JinaScoringOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Jina AI scoring model.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@ConfigurationProperties(JinaScoringProperties.CONFIG_PREFIX)
public class JinaScoringProperties {

	/**
	 * Configuration prefix for Jina AI scoring properties.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.jina.scoring";

	/**
	 * Default base URL for the Jina AI API.
	 */
	public static final String DEFAULT_BASE_URL = "https://api.jina.ai";

	/**
	 * Enable Jina scoring model.
	 */
	private boolean enabled = true;

	/**
	 * The URL to connect to.
	 */
	private String baseUrl = DEFAULT_BASE_URL;

	/**
	 * Jina AI API Key.
	 */
	private @Nullable String apiKey;

	/**
	 * Default options for Jina scoring.
	 */
	@NestedConfigurationProperty
	private final JinaScoringOptions options = JinaScoringOptions.builder().build();

	/**
	 * Check if the Jina scoring model is enabled.
	 * @return true if enabled
	 */
	public boolean isEnabled() {
		return this.enabled;
	}

	/**
	 * Set whether to enable the Jina scoring model.
	 * @param enabled the enabled flag
	 */
	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Get the base URL for the Jina AI API.
	 * @return the base URL
	 */
	public String getBaseUrl() {
		return this.baseUrl;
	}

	/**
	 * Set the base URL for the Jina AI API.
	 * @param baseUrl the base URL
	 */
	public void setBaseUrl(final String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/**
	 * Get the Jina AI API Key.
	 * @return the API key
	 */
	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	/**
	 * Set the Jina AI API Key.
	 * @param apiKey the API key
	 */
	public void setApiKey(final String apiKey) {
		this.apiKey = apiKey;
	}

	/**
	 * Get the default options for Jina scoring.
	 * @return the Jina scoring options
	 */
	public JinaScoringOptions getOptions() {
		return this.options;
	}

}
