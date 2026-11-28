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

package org.springframework.ai.model.openai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the properties-backed {@link OpenAiConnectionDetails} unless the application
 * (or a service connection) already provides one.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@Configuration(proxyBeanMethods = false)
class OpenAiConnectionDetailsConfiguration {

	@Bean
	@ConditionalOnMissingBean(OpenAiConnectionDetails.class)
	PropertiesOpenAiConnectionDetails openAiConnectionDetails(OpenAiCommonProperties properties) {
		return new PropertiesOpenAiConnectionDetails(properties);
	}

	/**
	 * {@link OpenAiConnectionDetails} backed by the {@code spring.ai.openai.base-url} and
	 * {@code spring.ai.openai.api-key} properties.
	 */
	static final class PropertiesOpenAiConnectionDetails implements OpenAiConnectionDetails {

		private final AbstractOpenAiProperties properties;

		PropertiesOpenAiConnectionDetails(AbstractOpenAiProperties properties) {
			this.properties = properties;
		}

		@Override
		public @Nullable String getBaseUrl() {
			return this.properties.getBaseUrl();
		}

		@Override
		public @Nullable String getApiKey() {
			return this.properties.getApiKey();
		}

	}

}
