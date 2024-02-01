/*
 * Copyright 2024-2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.stabilityai;

import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Mark Pollack
 * @since 0.8.0
 */
@ConfigurationProperties(StabilityAiProperties.CONFIG_PREFIX)
public class StabilityAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.stabilityai";

	private String apiKey;

	private String baseUrl = StabilityAiApi.DEFAULT_BASE_URL;

	@NestedConfigurationProperty
	private StabilityAiImageOptions options;

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

	public StabilityAiImageOptions getOptions() {
		return this.options;
	}

	public void setOptions(StabilityAiImageOptions options) {
		this.options = options;
	}

}
