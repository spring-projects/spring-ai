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

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsPropertyMapper;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama API.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author yinh
 * @since 0.8.0
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OllamaApi.class)
@EnableConfigurationProperties(OllamaConnectionProperties.class)
public class OllamaApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(OllamaConnectionDetails.class)
	PropertiesOllamaConnectionDetails ollamaConnectionDetails(OllamaConnectionProperties properties) {
		return new PropertiesOllamaConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails,
			OllamaConnectionProperties connectionProperties,
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

		return OllamaApi.builder()
			.baseUrl(connectionDetails.getBaseUrl())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
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
