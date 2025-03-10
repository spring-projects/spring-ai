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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class OpenAIAutoConfigurationUtil {

	protected static @NotNull ResolvedConnectionProperties resolveConnectionProperties(
			OpenAiParentProperties commonProperties, OpenAiParentProperties modelProperties, String modelType) {

		String baseUrl = StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl();
		String apiKey = StringUtils.hasText(modelProperties.getApiKey()) ? modelProperties.getApiKey()
				: commonProperties.getApiKey();
		String projectId = StringUtils.hasText(modelProperties.getProjectId()) ? modelProperties.getProjectId()
				: commonProperties.getProjectId();
		String organizationId = StringUtils.hasText(modelProperties.getOrganizationId())
				? modelProperties.getOrganizationId() : commonProperties.getOrganizationId();

		Map<String, List<String>> connectionHeaders = new HashMap<>();
		if (StringUtils.hasText(projectId)) {
			connectionHeaders.put("OpenAI-Project", List.of(projectId));
		}
		if (StringUtils.hasText(organizationId)) {
			connectionHeaders.put("OpenAI-Organization", List.of(organizationId));
		}

		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the connection property: spring.ai.openai.base-url or spring.ai.openai."
						+ modelType + ".base-url property.");
		Assert.hasText(apiKey,
				"OpenAI API key must be set. Use the connection property: spring.ai.openai.api-key or spring.ai.openai."
						+ modelType + ".api-key property.");

		return new ResolvedConnectionProperties(baseUrl, apiKey, CollectionUtils.toMultiValueMap(connectionHeaders));
	}

	public record ResolvedConnectionProperties(String baseUrl, String apiKey, MultiValueMap<String, String> headers) {

	}

}
