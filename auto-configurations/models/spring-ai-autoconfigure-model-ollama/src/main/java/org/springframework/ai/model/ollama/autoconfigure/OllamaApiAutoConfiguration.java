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

package org.springframework.ai.model.ollama.autoconfigure;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama API.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(OllamaApi.class)
@EnableConfigurationProperties(OllamaConnectionProperties.class)
public class OllamaApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(OllamaConnectionDetails.class)
	public PropertiesOllamaConnectionDetails ollamaConnectionDetails(OllamaConnectionProperties properties) {
		return new PropertiesOllamaConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
		return OllamaApi.builder()
			.baseUrl(connectionDetails.getBaseUrl())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.build();
	}

	static class PropertiesOllamaConnectionDetails implements OllamaConnectionDetails {

		private final OllamaConnectionProperties properties;

		PropertiesOllamaConnectionDetails(OllamaConnectionProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getBaseUrl() {
			return this.properties.getBaseUrl();
		}

	}

}
