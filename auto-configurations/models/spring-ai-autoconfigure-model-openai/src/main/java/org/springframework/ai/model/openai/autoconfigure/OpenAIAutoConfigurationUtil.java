/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class OpenAIAutoConfigurationUtil {

	private OpenAIAutoConfigurationUtil() {
		// Avoids instantiation
	}

	public static @NonNull ResolvedConnectionProperties resolveConnectionProperties(
			OpenAiParentProperties commonProperties, OpenAiParentProperties modelProperties, String modelType) {

		String baseUrl = StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl();
		String apiKey = StringUtils.hasText(modelProperties.getApiKey()) ? modelProperties.getApiKey()
				: commonProperties.getApiKey();
		String projectId = StringUtils.hasText(modelProperties.getProjectId()) ? modelProperties.getProjectId()
				: commonProperties.getProjectId();
		String organizationId = StringUtils.hasText(modelProperties.getOrganizationId())
				? modelProperties.getOrganizationId() : commonProperties.getOrganizationId();

		HttpHeaders connectionHeaders = new HttpHeaders();
		if (StringUtils.hasText(projectId)) {
			connectionHeaders.add("OpenAI-Project", projectId);
		}
		if (StringUtils.hasText(organizationId)) {
			connectionHeaders.add("OpenAI-Organization", organizationId);
		}

		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the connection property: spring.ai.openai.base-url or spring.ai.openai."
						+ modelType + ".base-url property.");
		Assert.hasText(apiKey,
				"OpenAI API key must be set. Use the connection property: spring.ai.openai.api-key or spring.ai.openai."
						+ modelType + ".api-key property.");

		return new ResolvedConnectionProperties(baseUrl, apiKey, connectionHeaders);
	}

	public record ResolvedConnectionProperties(String baseUrl, String apiKey, HttpHeaders headers) {

	}

}
