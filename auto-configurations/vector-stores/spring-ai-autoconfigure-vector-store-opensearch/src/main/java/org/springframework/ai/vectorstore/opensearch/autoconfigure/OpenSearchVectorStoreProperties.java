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

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = OpenSearchVectorStoreProperties.CONFIG_PREFIX)
public class OpenSearchVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.opensearch";

	/**
	 * Comma-separated list of the OpenSearch instances to use.
	 */
	private List<String> uris = List.of();

	private String indexName;

	private String username;

	private String password;

	private String mappingJson;

	/**
	 * SSL Bundle name ({@link org.springframework.boot.ssl.SslBundles}).
	 */
	private String sslBundle;

	/**
	 *
	 */
	private Duration connectionTimeout;

	private Duration readTimeout;

	private Aws aws = new Aws();

	public List<String> getUris() {
		return this.uris;
	}

	public void setUris(List<String> uris) {
		this.uris = uris;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getMappingJson() {
		return this.mappingJson;
	}

	public void setMappingJson(String mappingJson) {
		this.mappingJson = mappingJson;
	}

	public String getSslBundle() {
		return sslBundle;
	}

	public void setSslBundle(String sslBundle) {
		this.sslBundle = sslBundle;
	}

	public Duration getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public Aws getAws() {
		return this.aws;
	}

	public void setAws(Aws aws) {
		this.aws = aws;
	}

	static class Aws {

		private String domainName;

		private String host;

		private String serviceName;

		private String accessKey;

		private String secretKey;

		private String region;

		public String getDomainName() {
			return this.domainName;
		}

		public void setDomainName(String domainName) {
			this.domainName = domainName;
		}

		public String getHost() {
			return this.host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getServiceName() {
			return this.serviceName;
		}

		public void setServiceName(String serviceName) {
			this.serviceName = serviceName;
		}

		public String getAccessKey() {
			return this.accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return this.secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}

		public String getRegion() {
			return this.region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

	}

}
