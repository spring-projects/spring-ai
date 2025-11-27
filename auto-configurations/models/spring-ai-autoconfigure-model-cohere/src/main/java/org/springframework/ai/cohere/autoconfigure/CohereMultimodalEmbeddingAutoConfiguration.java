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

package org.springframework.ai.cohere.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.embedding.CohereMultimodalEmbeddingModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Multimodal Embedding {@link AutoConfiguration Auto-configuration} for Cohere
 *
 * @author Ricken Bazolo
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ CohereCommonProperties.class, CohereMultimodalEmbeddingProperties.class })
@ConditionalOnClass({ CohereApi.class, CohereMultimodalEmbeddingModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.MULTI_MODAL_EMBEDDING_MODEL, havingValue = SpringAIModels.COHERE,
		matchIfMissing = true)
public class CohereMultimodalEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CohereMultimodalEmbeddingModel cohereMultimodalEmbeddingModel(CohereCommonProperties commonProperties,
			CohereMultimodalEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry) {

		var cohereApi = cohereApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		return CohereMultimodalEmbeddingModel.builder()
			.cohereApi(cohereApi)
			.options(embeddingProperties.getOptions())
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
	}

	private CohereApi cohereApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Cohere API key must be set");
		Assert.hasText(resoledBaseUrl, "Cohere base URL must be set");

		return CohereApi.builder()
			.baseUrl(resoledBaseUrl)
			.apiKey(resolvedApiKey)
			.restClientBuilder(restClientBuilder)
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

}
