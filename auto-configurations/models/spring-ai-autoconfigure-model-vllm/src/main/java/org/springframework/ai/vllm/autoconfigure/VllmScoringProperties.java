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

package org.springframework.ai.vllm.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vllm.VllmScoringOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for vLLM Scoring API.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@ConfigurationProperties(VllmScoringProperties.CONFIG_PREFIX)
public class VllmScoringProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vllm.scoring";

	public static final String DEFAULT_BASE_URL = "http://localhost:8000";

	/** Enable vLLM scoring model. */
	private boolean enabled = true;

	/** vLLM server base URL. */
	private String baseUrl = DEFAULT_BASE_URL;

	/** vLLM API Key (Optional). */
	private @Nullable String apiKey;

	@NestedConfigurationProperty
	private VllmScoringOptions options = VllmScoringOptions.builder().build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public VllmScoringOptions getOptions() {
		return this.options;
	}

	public void setOptions(VllmScoringOptions options) {
		this.options = options;
	}

}
