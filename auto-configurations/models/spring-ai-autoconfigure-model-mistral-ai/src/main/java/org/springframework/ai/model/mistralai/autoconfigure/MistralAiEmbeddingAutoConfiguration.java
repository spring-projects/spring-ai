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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.api.MistralAiApi;
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
 * Embedding {@link AutoConfiguration Auto-configuration} for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Yanming Zhou
 * @author yinh
 * @since 0.8.1
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiEmbeddingProperties.class })
@ConditionalOnClass(MistralAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.MISTRAL,
		matchIfMissing = true)
public class MistralAiEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MistralAiEmbeddingModel mistralAiEmbeddingModel(MistralAiCommonProperties commonProperties,
			MistralAiEmbeddingProperties embeddingProperties,
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

		var mistralAiApi = mistralAiApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(), restClientBuilder,
				responseErrorHandler);

		var embeddingModel = MistralAiEmbeddingModel.builder()
			.mistralAiApi(mistralAiApi)
			.metadataMode(embeddingProperties.getMetadataMode())
			.options(embeddingProperties.getOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private MistralAiApi mistralAiApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resoledBaseUrl, "Mistral base URL must be set");

		return MistralAiApi.builder()
			.baseUrl(resoledBaseUrl)
			.apiKey(resolvedApiKey)
			.restClientBuilder(restClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

	private void applyRestClientSettings(RestClient.Builder builder, HttpClientSettings httpClientSettings,
			ClientHttpRequestFactoryBuilder<?> factoryBuilder) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(httpClientSettings);
		builder.requestFactory(requestFactory);
	}

}
