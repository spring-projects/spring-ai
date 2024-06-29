/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.azure.openai;

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
	 * Azure OpenAI API endpoint. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String endpoint;

	/**
	 * Azure OpenAI API authType. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String authType;

	/**
	 * Azure OpenAI API clientId. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String clientId;

	/**
	 * Azure OpenAI API clientSecret. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String clientSecret;

	/**
	 * Azure OpenAI API tenantId. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private String tenantId;

	/**
	 * Azure OpenAI API enableLog. From the Azure AI OpenAI `Keys and Endpoint` section
	 * under `Resource Management`.
	 */
	private Boolean enableLog;

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getAuthType() {
		return authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public Boolean getEnableLog() {
		return enableLog;
	}

	public void setEnableLog(Boolean enableLog) {
		this.enableLog = enableLog;
	}

}
