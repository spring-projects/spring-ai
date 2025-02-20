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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama Chat Client.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @since 0.8.0
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass(OllamaApi.class)
@EnableConfigurationProperties({ OllamaChatProperties.class, OllamaEmbeddingProperties.class,
		OllamaConnectionProperties.class, OllamaInitializationProperties.class })
@ImportAutoConfiguration(classes = { RestClientAutoConfiguration.class, ToolCallingAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class OllamaAutoConfiguration {

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
		return new OllamaApi(connectionDetails.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder));
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OllamaChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi, OllamaChatProperties properties,
			OllamaInitializationProperties initProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {
		var chatModelPullStrategy = initProperties.getChat().isInclude() ? initProperties.getPullModelStrategy()
				: PullModelStrategy.NEVER;

		var chatModel = OllamaChatModel.builder()
			.ollamaApi(ollamaApi)
			.defaultOptions(properties.getOptions())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.modelManagementOptions(
					new ModelManagementOptions(chatModelPullStrategy, initProperties.getChat().getAdditionalModels(),
							initProperties.getTimeout(), initProperties.getMaxRetries()))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OllamaEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi, OllamaEmbeddingProperties properties,
			OllamaInitializationProperties initProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
		var embeddingModelPullStrategy = initProperties.getEmbedding().isInclude()
				? initProperties.getPullModelStrategy() : PullModelStrategy.NEVER;

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(ollamaApi)
			.defaultOptions(properties.getOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.modelManagementOptions(new ModelManagementOptions(embeddingModelPullStrategy,
					initProperties.getEmbedding().getAdditionalModels(), initProperties.getTimeout(),
					initProperties.getMaxRetries()))
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
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
