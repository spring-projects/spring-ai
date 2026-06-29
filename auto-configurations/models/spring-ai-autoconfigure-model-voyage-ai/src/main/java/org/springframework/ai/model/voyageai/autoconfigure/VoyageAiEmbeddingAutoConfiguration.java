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

package org.springframework.ai.model.voyageai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.VoyageAiEmbeddingModel;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Embedding {@link AutoConfiguration Auto-configuration} for Voyage AI.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ VoyageAiCommonProperties.class, VoyageAiEmbeddingProperties.class })
@ConditionalOnClass(VoyageAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.VOYAGE,
		matchIfMissing = true)
public class VoyageAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VoyageAiEmbeddingModel voyageAiEmbeddingModel(VoyageAiCommonProperties commonProperties,
			VoyageAiEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var voyageAiApi = voyageAiApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var embeddingModel = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(voyageAiApi)
			.metadataMode(embeddingProperties.getMetadataMode())
			.options(embeddingProperties.toOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private VoyageAiApi voyageAiApi(@Nullable String apiKey, @Nullable String commonApiKey, @Nullable String baseUrl,
			@Nullable String commonBaseUrl, RestClient.Builder restClientBuilder,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Voyage AI API key must be set");
		Assert.hasText(resolvedBaseUrl, "Voyage AI base URL must be set");

		return VoyageAiApi.builder()
			.baseUrl(resolvedBaseUrl)
			.apiKey(resolvedApiKey)
			.restClientBuilder(restClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

}
