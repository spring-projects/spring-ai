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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
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

import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

/**
 * {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Christian Tzolov
 * @author Stefan Vassilev
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 * @author Yanming Zhou
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_SPEECH_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiAudioSpeechProperties.class })
public class OpenAiAudioSpeechAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiAudioSpeechModel openAiAudioSpeechModel(OpenAiConnectionProperties commonProperties,
			OpenAiAudioSpeechProperties speechProperties, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<SslBundles> sslBundles,
			ObjectProvider<HttpClientSettings> globalHttpClientSettings,
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factoryBuilder,
			ObjectProvider<ClientHttpConnectorBuilder<?>> webConnectorBuilderProvider) {

		OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
				commonProperties, speechProperties, "speech");

		HttpClientSettingsPropertyMapper mapper = new HttpClientSettingsPropertyMapper(sslBundles.getIfAvailable(),
				globalHttpClientSettings.getIfAvailable());
		HttpClientSettings httpClientSettings = mapper.map(commonProperties.getHttp());

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		applyRestClientSettings(restClientBuilder, httpClientSettings,
				factoryBuilder.getIfAvailable(ClientHttpRequestFactoryBuilder::detect));

		WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
		applyWebClientSettings(webClientBuilder, httpClientSettings,
				webConnectorBuilderProvider.getIfAvailable(ClientHttpConnectorBuilder::detect));

		var openAiAudioApi = OpenAiAudioApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.speechPath(speechProperties.getSpeechPath())
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();

		return new OpenAiAudioSpeechModel(openAiAudioApi, speechProperties.getOptions(),
				retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));
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
