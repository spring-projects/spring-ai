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

package org.springframework.ai.openaisdk;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

public class AbstractOpenAiSdkOptions {

	/**
	 * The deployment URL to connect to OpenAI.
	 */
	private String baseUrl;

	/**
	 * The API key to connect to OpenAI.
	 */
	private String apiKey;

	/**
	 * Credentials used to connect to Azure OpenAI.
	 */
	private Credential credential;

	/**
	 * The model name used. When using Azure AI Foundry, this is also used as the default
	 * deployment name.
	 */
	private String model;

	/**
	 * The deployment name as defined in Azure AI Foundry. On Azure AI Foundry, the
	 * default deployment name is the same as the model name. When using OpenAI directly,
	 * this value isn't used.
	 */
	private String azureDeploymentName;

	/**
	 * The Azure OpenAI Service version to use when connecting to Azure AI Foundry.
	 */
	private AzureOpenAIServiceVersion azureOpenAIServiceVersion;

	/**
	 * The organization ID to use when connecting to Azure OpenAI.
	 */
	private String organizationId;

	/**
	 * Whether Azure OpenAI is detected.
	 */
	private boolean isAzure;

	/**
	 * Whether GitHub Models is detected.
	 */
	private boolean isGitHubModels;

	/**
	 * Request timeout for OpenAI client.
	 */
	private Duration timeout;

	/**
	 * Maximum number of retries for OpenAI client.
	 */
	private Integer maxRetries;

	/**
	 * Proxy settings for OpenAI client.
	 */
	private Proxy proxy;

	/**
	 * Custom headers to add to OpenAI client requests.
	 */
	private Map<String, String> customHeaders;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public Credential getCredential() {
		return credential;
	}

	public void setCredential(Credential credential) {
		this.credential = credential;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getAzureDeploymentName() {
		return azureDeploymentName;
	}

	public void setAzureDeploymentName(String azureDeploymentName) {
		this.azureDeploymentName = azureDeploymentName;
	}

	/**
	 * Alias for getAzureDeploymentName()
	 */
	public String getDeploymentName() {
		return azureDeploymentName;
	}

	/**
	 * Alias for setAzureDeploymentName()
	 */
	public void setDeploymentName(String azureDeploymentName) {
		this.azureDeploymentName = azureDeploymentName;
	}

	public AzureOpenAIServiceVersion getAzureOpenAIServiceVersion() {
		return azureOpenAIServiceVersion;
	}

	public void setAzureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
		this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
	}

	public boolean isAzure() {
		return isAzure;
	}

	public void setAzure(boolean azure) {
		isAzure = azure;
	}

	public boolean isGitHubModels() {
		return isGitHubModels;
	}

	public void setGitHubModels(boolean gitHubModels) {
		isGitHubModels = gitHubModels;
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public Integer getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(Integer maxRetries) {
		this.maxRetries = maxRetries;
	}

	public Proxy getProxy() {
		return proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public Map<String, String> getCustomHeaders() {
		return customHeaders;
	}

	public void setCustomHeaders(Map<String, String> customHeaders) {
		this.customHeaders = customHeaders;
	}

}
