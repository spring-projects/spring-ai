/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.ollama;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama Chat Client.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @since 0.8.0
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(OllamaApi.class)
@EnableConfigurationProperties({ OllamaChatProperties.class, OllamaEmbeddingProperties.class,
		OllamaConnectionProperties.class })
public class OllamaAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(OllamaConnectionDetails.class)
	public PropertiesOllamaConnectionDetails ollamaConnectionDetails(OllamaConnectionProperties properties) {
		return new PropertiesOllamaConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails, RestClient.Builder restClientBuilder) {
		return new OllamaApi(connectionDetails.getBaseUrl(), restClientBuilder);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OllamaChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OllamaChatClient ollamaChatClient(OllamaApi ollamaApi, OllamaChatProperties properties) {

		return new OllamaChatClient(ollamaApi).withModel(properties.getModel())
			.withDefaultOptions(properties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OllamaEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OllamaEmbeddingClient ollamaEmbeddingClient(OllamaApi ollamaApi, OllamaEmbeddingProperties properties) {

		return new OllamaEmbeddingClient(ollamaApi).withModel(properties.getModel())
			.withDefaultOptions(properties.getOptions());
	}

	private static class PropertiesOllamaConnectionDetails implements OllamaConnectionDetails {

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
