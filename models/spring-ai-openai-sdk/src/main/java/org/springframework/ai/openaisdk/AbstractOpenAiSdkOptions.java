/*
 * Copyright 2025-2025 the original author or authors.
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

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

public class AbstractOpenAiSdkOptions {

	/**
	 * Default request timeout for the OpenAI client.
	 */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

	/**
	 * Default maximum number of retries for the OpenAI client.
	 */
	public static final int DEFAULT_MAX_RETRIES = 3;

	/**
	 * The deployment URL to connect to OpenAI.
	 */
	private @Nullable String baseUrl;

	/**
	 * The API key to connect to OpenAI.
	 */
	private @Nullable String apiKey;

	/**
	 * Credentials used to connect to Microsoft Foundry.
	 */
	private @Nullable Credential credential;

	/**
	 * The model name used. When using Microsoft Foundry, this is also used as the default
	 * deployment name.
	 */
	private @Nullable String model;

	/**
	 * The deployment name as defined in Microsoft Foundry. On Microsoft Foundry, the
	 * default deployment name is the same as the model name. When using OpenAI directly,
	 * this value isn't used.
	 */
	private @Nullable String microsoftDeploymentName;

	/**
	 * The Service version to use when connecting to Microsoft Foundry.
	 */
	private @Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion;

	/**
	 * The organization ID to use when connecting to Microsoft Foundry.
	 */
	private @Nullable String organizationId;

	/**
	 * Whether Microsoft Foundry is detected.
	 */
	private boolean isMicrosoftFoundry;

	/**
	 * Whether GitHub Models is detected.
	 */
	private boolean isGitHubModels;

	/**
	 * Request timeout for OpenAI client.
	 */
	private Duration timeout = DEFAULT_TIMEOUT;

	/**
	 * Maximum number of retries for OpenAI client.
	 */
	private int maxRetries = DEFAULT_MAX_RETRIES;

	/**
	 * Proxy settings for OpenAI client.
	 */
	private @Nullable Proxy proxy;

	/**
	 * Custom HTTP headers to add to OpenAI client requests.
	 */
	private Map<String, String> customHeaders = new HashMap<>();

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

	public @Nullable Credential getCredential() {
		return this.credential;
	}

	public void setCredential(@Nullable Credential credential) {
		this.credential = credential;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getMicrosoftDeploymentName() {
		return this.microsoftDeploymentName;
	}

	public void setMicrosoftDeploymentName(@Nullable String microsoftDeploymentName) {
		this.microsoftDeploymentName = microsoftDeploymentName;
	}

	/**
	 * Alias for getAzureDeploymentName()
	 */
	public @Nullable String getDeploymentName() {
		return this.microsoftDeploymentName;
	}

	/**
	 * Alias for setAzureDeploymentName()
	 */
	public void setDeploymentName(@Nullable String azureDeploymentName) {
		this.microsoftDeploymentName = azureDeploymentName;
	}

	public @Nullable AzureOpenAIServiceVersion getMicrosoftFoundryServiceVersion() {
		return this.microsoftFoundryServiceVersion;
	}

	public void setMicrosoftFoundryServiceVersion(@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion) {
		this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
	}

	public @Nullable String getOrganizationId() {
		return this.organizationId;
	}

	public void setOrganizationId(@Nullable String organizationId) {
		this.organizationId = organizationId;
	}

	public boolean isMicrosoftFoundry() {
		return this.isMicrosoftFoundry;
	}

	public void setMicrosoftFoundry(boolean microsoftFoundry) {
		this.isMicrosoftFoundry = microsoftFoundry;
	}

	public boolean isGitHubModels() {
		return this.isGitHubModels;
	}

	public void setGitHubModels(boolean gitHubModels) {
		this.isGitHubModels = gitHubModels;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
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

}
