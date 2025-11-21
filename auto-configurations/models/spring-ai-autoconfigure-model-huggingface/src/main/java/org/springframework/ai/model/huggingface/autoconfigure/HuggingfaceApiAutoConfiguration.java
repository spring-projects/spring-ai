/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.huggingface.autoconfigure;

import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for HuggingFace API.
 *
 * @author Myeongdeok Kang
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(HuggingfaceApi.class)
@EnableConfigurationProperties(HuggingfaceConnectionProperties.class)
public class HuggingfaceApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(HuggingfaceConnectionDetails.class)
	PropertiesHuggingfaceConnectionDetails huggingfaceConnectionDetails(HuggingfaceConnectionProperties properties) {
		return new PropertiesHuggingfaceConnectionDetails(properties);
	}

	// This bean is no longer created here since Chat and Embedding
	// need different base URLs. Each AutoConfiguration creates its own API instance.

	static class PropertiesHuggingfaceConnectionDetails implements HuggingfaceConnectionDetails {

		private final HuggingfaceConnectionProperties properties;

		PropertiesHuggingfaceConnectionDetails(HuggingfaceConnectionProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getApiKey() {
			return this.properties.getApiKey();
		}

	}

}
