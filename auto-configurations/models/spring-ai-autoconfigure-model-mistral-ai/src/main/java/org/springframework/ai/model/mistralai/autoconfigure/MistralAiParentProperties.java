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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Parent properties for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Nicolas Krier
 * @since 0.8.1
 */
public abstract class MistralAiParentProperties {

	private @Nullable String apiKey;

	private @Nullable String baseUrl;

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(@Nullable String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKeyOrDefaultFrom(MistralAiCommonProperties commonProperties) {
		var resolvedApiKey = StringUtils.hasText(this.apiKey) ? this.apiKey : commonProperties.getApiKey();
		Assert.state(StringUtils.hasText(resolvedApiKey), "Mistral AI API key must be set!");

		return resolvedApiKey;
	}

	public String getBaseUrlOrDefaultFrom(MistralAiCommonProperties commonProperties) {
		var resolvedBaseUrl = StringUtils.hasText(this.baseUrl) ? this.baseUrl : commonProperties.getBaseUrl();
		Assert.state(StringUtils.hasText(resolvedBaseUrl), "Mistral AI base URL must be set!");

		return resolvedBaseUrl;
	}

}
