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

package org.springframework.ai.model.azure.openai.autoconfigure;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(AzureOpenAiConnectionProperties.CONFIG_PREFIX)
public class AzureOpenAiConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai";

	/**
	 * Azure OpenAI API key. From the Azure AI OpenAI `Keys and Endpoint` section under
	 * `Resource Management`.
	 */
	private String apiKey;

	/**
	 * (non Azure) OpenAI API key. Used to authenticate with the OpenAI service, instead
	 * of Azure OpenAI. This automatically sets the endpoint to https://api.openai.com/v1.
	 */
	private String openAiApiKey;

	/**
	 * Azure OpenAI API endpoint. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String endpoint;

	private Map<String, String> customHeaders = new HashMap<>();

	/**
	 * HTTP connection timeout
	 */
	private Duration connectTimeout;

	/**
	 * HTTP read timeout
	 */
	private Duration readTimeout;

	/**
	 * HTTP write timeout
	 */
	private Duration writeTimeout;

	/**
	 * HTTP response timeout
	 */
	private Duration responseTimeout;

	/**
	 * The maximum number of connections in the HTTP connection pool
	 */
	private Integer maximumConnectionPoolSize;

	public String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getOpenAiApiKey() {
		return this.openAiApiKey;
	}

	public void setOpenAiApiKey(String openAiApiKey) {
		this.openAiApiKey = openAiApiKey;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	public void setCustomHeaders(Map<String, String> customHeaders) {
		this.customHeaders = customHeaders;
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

	public Duration getWriteTimeout() {
		return this.writeTimeout;
	}

	public void setWriteTimeout(Duration writeTimeout) {
		this.writeTimeout = writeTimeout;
	}

	public Duration getResponseTimeout() {
		return this.responseTimeout;
	}

	public void setResponseTimeout(Duration responseTimeout) {
		this.responseTimeout = responseTimeout;
	}

	public Integer getMaximumConnectionPoolSize() {
		return this.maximumConnectionPoolSize;
	}

	public void setMaximumConnectionPoolSize(Integer maximumConnectionPoolSize) {
		this.maximumConnectionPoolSize = maximumConnectionPoolSize;
	}

}
