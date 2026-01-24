/*
 * Copyright 2026-2026 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Utility class that provides helper methods for working with Mistral AI properties.
 *
 * @author Nicolas Krier
 */
public final class MistralAiPropertiesUtils {

	private MistralAiPropertiesUtils() {
	}

	/**
	 * Resolves the Mistral AI API key by checking the specific properties first, falling
	 * back to the common properties if the specific API key is not set.
	 * @param commonProperties the common Mistral AI properties, which may contain a
	 * default API key
	 * @param specificProperties the specific Mistral AI properties, which may override
	 * the common API key
	 * @return the resolved Mistral AI API key, either from the specific or common
	 * properties
	 * @throws IllegalArgumentException if neither the specific nor the common API key is
	 * set or is empty
	 */
	public static String resolveApiKey(MistralAiCommonProperties commonProperties,
			MistralAiParentProperties specificProperties) {
		var commonApiKey = commonProperties.getApiKey();
		var specificApiKey = specificProperties.getApiKey();
		var resolvedApiKey = StringUtils.hasText(specificApiKey) ? specificApiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "Mistral AI API key must be set!");

		return resolvedApiKey;
	}

	/**
	 * Resolves the Mistral AI base URL by checking the specific properties first, falling
	 * back to the common properties if the specific base URL is not set.
	 * @param commonProperties the common Mistral AI properties, which may contain a
	 * default base URL
	 * @param specificProperties the specific Mistral AI properties, which may override
	 * the common base URL
	 * @return the resolved Mistral AI base URL, either from the specific or common
	 * properties
	 * @throws IllegalArgumentException if neither the specific nor the common base URL is
	 * set or is empty
	 */
	public static String resolveBaseUrl(MistralAiCommonProperties commonProperties,
			MistralAiParentProperties specificProperties) {
		var commonBaseUrl = commonProperties.getBaseUrl();
		var specificBaseUrl = specificProperties.getBaseUrl();
		var resolvedBaseUrl = StringUtils.hasText(specificBaseUrl) ? specificBaseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "Mistral AI base URL must be set!");

		return resolvedBaseUrl;
	}

}
