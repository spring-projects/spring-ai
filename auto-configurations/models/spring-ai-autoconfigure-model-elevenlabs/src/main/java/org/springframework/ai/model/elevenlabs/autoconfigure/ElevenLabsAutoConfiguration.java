/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.model.elevenlabs.autoconfigure;

import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechModel;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
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
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for ElevenLabs.
 *
 * @author Alexandros Pappas
 * @author Yanming Zhou
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
		WebClientAutoConfiguration.class })
@ConditionalOnClass(ElevenLabsApi.class)
@EnableConfigurationProperties({ ElevenLabsSpeechProperties.class, ElevenLabsConnectionProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_SPEECH_MODEL, havingValue = SpringAIModels.ELEVEN_LABS,
		matchIfMissing = true)
public class ElevenLabsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ElevenLabsApi elevenLabsApi(ElevenLabsConnectionProperties connectionProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<SslBundles> sslBundles,
			ObjectProvider<HttpClientSettings> globalHttpClientSettings,
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factoryBuilder,
			ObjectProvider<ClientHttpConnectorBuilder<?>> webConnectorBuilderProvider) {

		HttpClientSettingsPropertyMapper mapper = new HttpClientSettingsPropertyMapper(sslBundles.getIfAvailable(),
				globalHttpClientSettings.getIfAvailable());
		HttpClientSettings httpClientSettings = mapper.map(connectionProperties.getHttp());

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		applyRestClientSettings(restClientBuilder, httpClientSettings,
				factoryBuilder.getIfAvailable(ClientHttpRequestFactoryBuilder::detect));

		WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
		applyWebClientSettings(webClientBuilder, httpClientSettings,
				webConnectorBuilderProvider.getIfAvailable(ClientHttpConnectorBuilder::detect));

		return ElevenLabsApi.builder()
			.baseUrl(connectionProperties.getBaseUrl())
			.apiKey(connectionProperties.getApiKey())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ElevenLabsTextToSpeechModel elevenLabsSpeechModel(ElevenLabsApi elevenLabsApi,
			ElevenLabsSpeechProperties speechProperties, ObjectProvider<RetryTemplate> retryTemplate) {

		return ElevenLabsTextToSpeechModel.builder()
			.elevenLabsApi(elevenLabsApi)
			.defaultOptions(speechProperties.getOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
	}

	private void applyRestClientSettings(RestClient.Builder builder, HttpClientSettings httpClientSettings,
			ClientHttpRequestFactoryBuilder<?> factoryBuilder) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(httpClientSettings);
		builder.requestFactory(requestFactory);
	}

	private void applyWebClientSettings(WebClient.Builder builder, HttpClientSettings httpClientSettings,
			ClientHttpConnectorBuilder<?> connectorBuilder) {
		ClientHttpConnector connector = connectorBuilder.build(httpClientSettings);
		builder.clientConnector(connector);
	}

}
