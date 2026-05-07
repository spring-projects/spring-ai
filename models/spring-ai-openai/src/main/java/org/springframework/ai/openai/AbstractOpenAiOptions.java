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

package org.springframework.ai.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

public class AbstractOpenAiOptions {

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

	protected AbstractOpenAiOptions() {
	}

	protected AbstractOpenAiOptions(@Nullable String baseUrl, @Nullable String apiKey, @Nullable Credential credential,
			@Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.credential = credential;
		this.model = model;
		this.microsoftDeploymentName = microsoftDeploymentName;
		this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
		this.organizationId = organizationId;
		this.isMicrosoftFoundry = isMicrosoftFoundry != null ? isMicrosoftFoundry : false;
		this.isGitHubModels = isGitHubModels != null ? isGitHubModels : false;
		this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
		this.maxRetries = maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES;
		this.proxy = proxy;
		this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : new HashMap<>();
	}

	public @Nullable String getBaseUrl() {
		return this.baseUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public @Nullable Credential getCredential() {
		return this.credential;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public @Nullable String getMicrosoftDeploymentName() {
		return this.microsoftDeploymentName;
	}

	/**
	 * Alias for getAzureDeploymentName()
	 */
	public @Nullable String getDeploymentName() {
		return this.microsoftDeploymentName;
	}

	public @Nullable AzureOpenAIServiceVersion getMicrosoftFoundryServiceVersion() {
		return this.microsoftFoundryServiceVersion;
	}

	public @Nullable String getOrganizationId() {
		return this.organizationId;
	}

	public boolean isMicrosoftFoundry() {
		return this.isMicrosoftFoundry;
	}

	public boolean isGitHubModels() {
		return this.isGitHubModels;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	public @Nullable Proxy getProxy() {
		return this.proxy;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

}
