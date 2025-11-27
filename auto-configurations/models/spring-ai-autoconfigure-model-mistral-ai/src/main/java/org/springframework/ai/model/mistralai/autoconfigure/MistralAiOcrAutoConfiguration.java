/*
 * Copyright 2025-2025 the original author or authors.
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

import org.springframework.ai.mistralai.ocr.MistralOcrApi;
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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OCR {@link AutoConfiguration Auto-configuration} for Mistral AI OCR.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(MistralOcrApi.class)
@ConditionalOnProperty(name = "spring.ai.model.ocr", havingValue = SpringAIModels.MISTRAL, matchIfMissing = true)
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiOcrProperties.class })
public class MistralAiOcrAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MistralOcrApi mistralOcrApi(MistralAiCommonProperties commonProperties, MistralAiOcrProperties ocrProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<SslBundles> sslBundles,
			ObjectProvider<HttpClientSettings> globalHttpClientSettings,
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factoryBuilder) {

		var apiKey = ocrProperties.getApiKey();
		var baseUrl = ocrProperties.getBaseUrl();

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonProperties.getApiKey();
		var resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonProperties.getBaseUrl();

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resolvedBaseUrl, "Mistral base URL must be set");

		HttpClientSettingsPropertyMapper mapper = new HttpClientSettingsPropertyMapper(sslBundles.getIfAvailable(),
				globalHttpClientSettings.getIfAvailable());
		HttpClientSettings httpClientSettings = mapper.map(commonProperties);

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		applyRestClientSettings(restClientBuilder, httpClientSettings,
				factoryBuilder.getIfAvailable(ClientHttpRequestFactoryBuilder::detect));

		return new MistralOcrApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder,
				responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
	}

	private void applyRestClientSettings(RestClient.Builder builder, HttpClientSettings httpClientSettings,
										 ClientHttpRequestFactoryBuilder<?> factoryBuilder) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(httpClientSettings);
		builder.requestFactory(requestFactory);
	}

}
