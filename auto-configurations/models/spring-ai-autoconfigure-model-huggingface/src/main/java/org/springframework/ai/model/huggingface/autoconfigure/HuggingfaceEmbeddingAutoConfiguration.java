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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.huggingface.HuggingfaceEmbeddingModel;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for HuggingFace Embedding Model.
 *
 * @author Myeongdeok Kang
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
		HuggingfaceApiAutoConfiguration.class })
@ConditionalOnClass(HuggingfaceApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.HUGGINGFACE,
		matchIfMissing = true)
@EnableConfigurationProperties({ HuggingfaceConnectionProperties.class, HuggingfaceEmbeddingProperties.class })
public class HuggingfaceEmbeddingAutoConfiguration {

	@Bean
	@Qualifier("huggingfaceEmbeddingApi")
	@ConditionalOnMissingBean(name = "huggingfaceEmbeddingApi")
	@ConditionalOnProperty(prefix = HuggingfaceEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public HuggingfaceApi huggingfaceEmbeddingApi(HuggingfaceConnectionDetails connectionDetails,
			HuggingfaceEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider) {

		String apiKey = connectionDetails.getApiKey();
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"HuggingFace API key must be set. Please configure spring.ai.huggingface.api-key");
		}

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		ResponseErrorHandler responseErrorHandler = responseErrorHandlerProvider.getIfAvailable(() -> null);

		HuggingfaceApi.Builder apiBuilder = HuggingfaceApi.builder()
			.baseUrl(embeddingProperties.getUrl())
			.apiKey(apiKey)
			.restClientBuilder(restClientBuilder);

		if (responseErrorHandler != null) {
			apiBuilder.responseErrorHandler(responseErrorHandler);
		}

		return apiBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = HuggingfaceEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public HuggingfaceEmbeddingModel huggingfaceEmbeddingModel(
			@Qualifier("huggingfaceEmbeddingApi") HuggingfaceApi huggingfaceApi,
			HuggingfaceEmbeddingProperties embeddingProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention, RetryTemplate retryTemplate) {

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(huggingfaceApi)
			.defaultOptions(embeddingProperties.getOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.retryTemplate(retryTemplate)
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

}
