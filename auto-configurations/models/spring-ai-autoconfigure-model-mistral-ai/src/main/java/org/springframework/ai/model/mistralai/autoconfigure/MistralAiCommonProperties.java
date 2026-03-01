/*
 * Copyright 2023-2026 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Common properties for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Nicolas Krier
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiCommonProperties.CONFIG_PREFIX)
public class MistralAiCommonProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai";

	public static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private static final String DEFAULTING_EXCEPTION_MESSAGE = "Mistral AI common properties can't be defaulted!";

	public MistralAiCommonProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

	@Override
	public String getApiKeyOrDefaultFrom(MistralAiCommonProperties commonProperties) {
		throw new UnsupportedOperationException(DEFAULTING_EXCEPTION_MESSAGE);
	}

	@Override
	public String getBaseUrlOrDefaultFrom(MistralAiCommonProperties commonProperties) {
		throw new UnsupportedOperationException(DEFAULTING_EXCEPTION_MESSAGE);
	}

}
