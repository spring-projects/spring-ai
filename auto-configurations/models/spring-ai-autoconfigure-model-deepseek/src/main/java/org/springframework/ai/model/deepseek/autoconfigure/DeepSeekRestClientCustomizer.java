/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.deepseek.autoconfigure;

import jakarta.annotation.Nullable;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * This customizer applies HTTP client settings (timeout, SSL, redirects) to
 * RestClient.Builder in a non-invasive way, preserving any existing configuration that
 * users may have already applied.
 *
 * @author yinh
 */
public record DeepSeekRestClientCustomizer(DeepSeekConnectionProperties.HttpClientConfig httpClientConfig,
		SslBundles sslBundles) implements RestClientCustomizer {

	public DeepSeekRestClientCustomizer(DeepSeekConnectionProperties.HttpClientConfig httpClientConfig,
			@Nullable SslBundles sslBundles) {
		this.httpClientConfig = httpClientConfig;
		this.sslBundles = sslBundles;
	}

	@Override
	public void customize(RestClient.Builder restClientBuilder) {
		if (!this.httpClientConfig.isEnabled()) {
			return;
		}

		// 将配置转换为 HttpClientSettings
		HttpClientSettings settings = this.httpClientConfig.toHttpClientSettings(this.sslBundles);

		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

		restClientBuilder.requestFactory(requestFactory);
	}
}
