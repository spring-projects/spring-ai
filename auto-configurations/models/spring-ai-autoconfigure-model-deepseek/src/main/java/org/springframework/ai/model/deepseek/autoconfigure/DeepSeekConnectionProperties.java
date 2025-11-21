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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

/**
 * Parent properties for DeepSeek.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(DeepSeekConnectionProperties.CONFIG_PREFIX)
public class DeepSeekConnectionProperties extends DeepSeekParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.deepseek";

	public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

	/**
	 * HTTP client settings for DeepSeek API calls.
	 */
	@NestedConfigurationProperty
	private HttpClientConfig httpClient = new HttpClientConfig();

	public DeepSeekConnectionProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

	public HttpClientConfig getHttpClient() {
		return this.httpClient;
	}

	public void setHttpClient(HttpClientConfig httpClient) {
		this.httpClient = httpClient;
	}

	/**
	 * HTTP client configuration settings. This inner class mirrors the structure of
	 * Spring Boot's HttpClientSettings to provide full control over HTTP client behavior.
	 */
	public static class HttpClientConfig {

		/**
		 * Whether to enable custom HTTP client configuration.
		 */
		private boolean enabled = true;

		/**
		 * Connection timeout.
		 */
		private Duration connectTimeout = Duration.ofSeconds(10);

		/**
		 * Read timeout.
		 */
		private Duration readTimeout = Duration.ofSeconds(60);

		/**
		 * HTTP redirect strategy.
		 */
		private HttpRedirects redirects;

		/**
		 * SSL bundle name for secure connections.
		 */
		private String sslBundle;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getConnectTimeout() {
			return this.connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return this.readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		public HttpRedirects getRedirects() {
			return this.redirects;
		}

		public void setRedirects(HttpRedirects redirects) {
			this.redirects = redirects;
		}

		public String getSslBundle() {
			return this.sslBundle;
		}

		public void setSslBundle(String sslBundle) {
			this.sslBundle = sslBundle;
		}

		/**
		 * Convert to Spring Boot's HttpClientSettings.
		 * @param sslBundles the SSL bundles registry
		 * @return HttpClientSettings instance
		 */
		public HttpClientSettings toHttpClientSettings(SslBundles sslBundles) {
			SslBundle bundle = (this.sslBundle != null && sslBundles != null) ? sslBundles.getBundle(this.sslBundle)
					: null;

			return HttpClientSettings.defaults()
				.withConnectTimeout(this.connectTimeout)
				.withReadTimeout(this.readTimeout)
				.withRedirects(this.redirects)
				.withSslBundle(bundle);
		}

	}

}
