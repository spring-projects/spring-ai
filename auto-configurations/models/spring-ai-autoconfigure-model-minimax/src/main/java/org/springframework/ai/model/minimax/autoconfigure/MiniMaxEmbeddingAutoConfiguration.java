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

package org.springframework.ai.model.minimax.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsPropertyMapper;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for MiniMax Embedding Model.
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 * @author Yanming Zhou
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(MiniMaxApi.class)
@EnableConfigurationProperties({ MiniMaxConnectionProperties.class, MiniMaxEmbeddingProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.MINIMAX,
		matchIfMissing = true)
public class MiniMaxEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MiniMaxEmbeddingModel miniMaxEmbeddingModel(MiniMaxConnectionProperties commonProperties,
			MiniMaxEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention,
			ObjectProvider<SslBundles> sslBundles,
			ObjectProvider<HttpClientSettings> globalHttpClientSettings,
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factoryBuilder) {

		HttpClientSettingsPropertyMapper mapper = new HttpClientSettingsPropertyMapper(sslBundles.getIfAvailable(),
				globalHttpClientSettings.getIfAvailable());
		HttpClientSettings httpClientSettings = mapper.map(commonProperties.getHttp());

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		applyRestClientSettings(restClientBuilder, httpClientSettings,
				factoryBuilder.getIfAvailable(ClientHttpRequestFactoryBuilder::detect));

		var miniMaxApi = miniMaxApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var embeddingModel = new MiniMaxEmbeddingModel(miniMaxApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private MiniMaxApi miniMaxApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "MiniMax base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "MiniMax API key must be set");

		return new MiniMaxApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder,
				responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
	}

	private void applyRestClientSettings(RestClient.Builder builder, HttpClientSettings httpClientSettings,
			ClientHttpRequestFactoryBuilder<?> factoryBuilder) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(httpClientSettings);
		builder.requestFactory(requestFactory);
	}

}
