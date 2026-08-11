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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Anthropic connection properties.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author dragonfsky
 * @since 2.0.0
 */
@ConfigurationProperties(AnthropicConnectionProperties.CONFIG_PREFIX)
public class AnthropicConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic";

	private @Nullable String baseUrl;

	private @Nullable String apiKey;

	private @Nullable Duration timeout;

	private @Nullable Integer maxRetries;

	private @Nullable Proxy proxy;

	private Map<String, String> customHeaders = new HashMap<>();

	/**
	 * Backend type for the Anthropic API. Defaults to
	 * {@link AnthropicBackendType#ANTHROPIC} (direct API). Set to
	 * {@link AnthropicBackendType#VERTEX_AI} for Google Vertex AI.
	 */
	private AnthropicBackendType backend = AnthropicBackendType.ANTHROPIC;

	/**
	 * Vertex AI specific configuration. Only used when {@link #backend} is
	 * {@link AnthropicBackendType#VERTEX_AI}.
	 */
	private Vertex vertex = new Vertex();

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(@Nullable String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public @Nullable Integer getMaxRetries() {
		return this.maxRetries;
	}

	public void setMaxRetries(@Nullable Integer maxRetries) {
		this.maxRetries = maxRetries;
	}

	public @Nullable Proxy getProxy() {
		return this.proxy;
	}

	public void setProxy(@Nullable Proxy proxy) {
		this.proxy = proxy;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	public void setCustomHeaders(Map<String, String> customHeaders) {
		this.customHeaders = customHeaders;
	}

	public AnthropicBackendType getBackend() {
		return this.backend;
	}

	public void setBackend(AnthropicBackendType backend) {
		this.backend = backend;
	}

	public Vertex getVertex() {
		return this.vertex;
	}

	public void setVertex(Vertex vertex) {
		this.vertex = vertex;
	}

	/**
	 * Google Cloud Vertex AI configuration for Anthropic Claude models.
	 */
	public static class Vertex {

		/**
		 * Google Cloud project ID.
		 */
		private @Nullable String projectId;

		/**
		 * Vertex AI location. Examples: {@code global}, {@code us}, {@code eu},
		 * {@code us-east5}.
		 */
		private @Nullable String location;

		/**
		 * Optional URI to a GCP service account JSON credentials file. When not
		 * configured, Application Default Credentials (ADC) are used.
		 */
		private @Nullable Resource credentialsUri;

		public @Nullable String getProjectId() {
			return this.projectId;
		}

		public void setProjectId(@Nullable String projectId) {
			this.projectId = projectId;
		}

		public @Nullable String getLocation() {
			return this.location;
		}

		public void setLocation(@Nullable String location) {
			this.location = location;
		}

		public @Nullable Resource getCredentialsUri() {
			return this.credentialsUri;
		}

		public void setCredentialsUri(@Nullable Resource credentialsUri) {
			this.credentialsUri = credentialsUri;
		}

	}

}
