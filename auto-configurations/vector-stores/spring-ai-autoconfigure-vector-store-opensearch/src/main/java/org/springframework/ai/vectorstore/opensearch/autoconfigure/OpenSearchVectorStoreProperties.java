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

package org.springframework.ai.vectorstore.opensearch.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = OpenSearchVectorStoreProperties.CONFIG_PREFIX)
public class OpenSearchVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.opensearch";

	/**
	 * Comma-separated list of the OpenSearch instances to use.
	 */
	private List<String> uris = List.of();

	private @Nullable String indexName;

	private @Nullable String username;

	private @Nullable String password;

	private @Nullable Boolean useApproximateKnn;

	private @Nullable Integer dimensions;

	private @Nullable String similarity;

	private @Nullable String mappingJson;

	/**
	 * SSL Bundle name ({@link org.springframework.boot.ssl.SslBundles}).
	 */
	private @Nullable String sslBundle;

	/**
	 * Time to wait until connection established. 0 - infinity.
	 */
	private @Nullable Duration connectionTimeout;

	/**
	 * Time to wait for response from the opposite endpoint. 0 - infinity.
	 */
	private @Nullable Duration readTimeout;

	/**
	 * Path prefix for OpenSearch API endpoints. Used when OpenSearch is behind a reverse
	 * proxy with a non-root path. For example, if your OpenSearch instance is accessible
	 * at https://example.com/opensearch/, set this to "/opensearch".
	 */
	private @Nullable String pathPrefix;

	private Aws aws = new Aws();

	public List<String> getUris() {
		return this.uris;
	}

	public void setUris(List<String> uris) {
		this.uris = uris;
	}

	public @Nullable String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public @Nullable String getUsername() {
		return this.username;
	}

	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	public @Nullable String getPassword() {
		return this.password;
	}

	public void setPassword(@Nullable String password) {
		this.password = password;
	}

	public @Nullable String getMappingJson() {
		return this.mappingJson;
	}

	public @Nullable Boolean getUseApproximateKnn() {
		return this.useApproximateKnn;
	}

	public void setUseApproximateKnn(@Nullable Boolean useApproximateKnn) {
		this.useApproximateKnn = useApproximateKnn;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public @Nullable String getSimilarity() {
		return this.similarity;
	}

	public void setSimilarity(@Nullable String similarity) {
		this.similarity = similarity;
	}

	public void setMappingJson(@Nullable String mappingJson) {
		this.mappingJson = mappingJson;
	}

	public @Nullable String getSslBundle() {
		return this.sslBundle;
	}

	public void setSslBundle(@Nullable String sslBundle) {
		this.sslBundle = sslBundle;
	}

	public @Nullable Duration getConnectionTimeout() {
		return this.connectionTimeout;
	}

	public void setConnectionTimeout(@Nullable Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public @Nullable Duration getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(@Nullable Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public @Nullable String getPathPrefix() {
		return this.pathPrefix;
	}

	public void setPathPrefix(@Nullable String pathPrefix) {
		this.pathPrefix = pathPrefix;
	}

	public Aws getAws() {
		return this.aws;
	}

	public void setAws(Aws aws) {
		this.aws = aws;
	}

	static class Aws {

		private @Nullable String domainName;

		private @Nullable String host;

		private @Nullable String serviceName;

		private @Nullable String accessKey;

		private @Nullable String secretKey;

		private @Nullable String region;

		public @Nullable String getDomainName() {
			return this.domainName;
		}

		public void setDomainName(@Nullable String domainName) {
			this.domainName = domainName;
		}

		public @Nullable String getHost() {
			return this.host;
		}

		public void setHost(@Nullable String host) {
			this.host = host;
		}

		public @Nullable String getServiceName() {
			return this.serviceName;
		}

		public void setServiceName(@Nullable String serviceName) {
			this.serviceName = serviceName;
		}

		public @Nullable String getAccessKey() {
			return this.accessKey;
		}

		public void setAccessKey(@Nullable String accessKey) {
			this.accessKey = accessKey;
		}

		public @Nullable String getSecretKey() {
			return this.secretKey;
		}

		public void setSecretKey(@Nullable String secretKey) {
			this.secretKey = secretKey;
		}

		public @Nullable String getRegion() {
			return this.region;
		}

		public void setRegion(@Nullable String region) {
			this.region = region;
		}

	}

}
