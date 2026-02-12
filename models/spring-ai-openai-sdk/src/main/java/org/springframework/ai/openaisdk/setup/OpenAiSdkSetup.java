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

package org.springframework.ai.openaisdk.setup;

import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps configure the OpenAI Java SDK, depending on the platform used. This code is
 * inspired by LangChain4j's
 * `dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper` class, which is
 * coded by the same author (Julien Dubois, from Microsoft).
 *
 * @author Julien Dubois
 */
public final class OpenAiSdkSetup {

	static final String OPENAI_URL = "https://api.openai.com/v1";
	static final String OPENAI_API_KEY = "OPENAI_API_KEY";
	static final String MICROSOFT_FOUNDRY_API_KEY = "MICROSOFT_FOUNDRY_API_KEY";
	static final String GITHUB_MODELS_URL = "https://models.github.ai/inference";
	static final String GITHUB_TOKEN = "GITHUB_TOKEN";
	static final String DEFAULT_USER_AGENT = "spring-ai-openai-sdk";

	private static final Logger logger = LoggerFactory.getLogger(OpenAiSdkSetup.class);

	private OpenAiSdkSetup() {
	}

	public enum ModelProvider {

		OPEN_AI, MICROSOFT_FOUNDRY, GITHUB_MODELS

	}

	public static OpenAIClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			boolean isAzure, boolean isGitHubModels, @Nullable String modelName, Duration timeout, int maxRetries,
			@Nullable Proxy proxy, @Nullable Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelProvider = detectModelProvider(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);
		OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();
		builder.baseUrl(calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName));

		String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
		if (calculatedApiKey != null) {
			builder.apiKey(calculatedApiKey);
		}
		else {
			if (credential != null) {
				builder.credential(credential);
			}
			else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
				// If no API key is provided for Microsoft Foundry, we try to use
				// passwordless
				// authentication
				builder.credential(azureAuthentication());
			}
		}
		builder.organization(organizationId);

		if (azureOpenAiServiceVersion != null) {
			builder.azureServiceVersion(azureOpenAiServiceVersion);
		}

		if (proxy != null) {
			builder.proxy(proxy);
		}

		builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
		if (customHeaders != null) {
			builder.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		builder.timeout(timeout);
		builder.maxRetries(maxRetries);
		return builder.build();
	}

	/**
	 * The asynchronous client setup is the same as the synchronous one in the OpenAI Java
	 * SDK, but uses a different client implementation.
	 */
	public static OpenAIClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			boolean isAzure, boolean isGitHubModels, @Nullable String modelName, Duration timeout, int maxRetries,
			@Nullable Proxy proxy, @Nullable Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelProvider = detectModelProvider(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);
		OpenAIOkHttpClientAsync.Builder builder = OpenAIOkHttpClientAsync.builder();
		builder.baseUrl(calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName));

		String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
		if (calculatedApiKey != null) {
			builder.apiKey(calculatedApiKey);
		}
		else {
			if (credential != null) {
				builder.credential(credential);
			}
			else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
				// If no API key is provided for Microsoft Foundry, we try to use
				// passwordless
				// authentication
				builder.credential(azureAuthentication());
			}
		}
		builder.organization(organizationId);

		if (azureOpenAiServiceVersion != null) {
			builder.azureServiceVersion(azureOpenAiServiceVersion);
		}

		if (proxy != null) {
			builder.proxy(proxy);
		}

		builder.putHeader("User-Agent", DEFAULT_USER_AGENT);
		if (customHeaders != null) {
			builder.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		builder.timeout(timeout);
		builder.maxRetries(maxRetries);
		return builder.build();
	}

	static @Nullable String detectBaseUrlFromEnv(@Nullable String baseUrl) {
		if (baseUrl == null) {
			var openAiBaseUrl = System.getenv("OPENAI_BASE_URL");
			if (openAiBaseUrl != null) {
				baseUrl = openAiBaseUrl;
				logger.debug("OpenAI Base URL detected from environment variable OPENAI_BASE_URL.");
			}
			var azureOpenAiBaseUrl = System.getenv("AZURE_OPENAI_BASE_URL");
			if (azureOpenAiBaseUrl != null) {
				baseUrl = azureOpenAiBaseUrl;
				logger.debug("Microsoft Foundry Base URL detected from environment variable AZURE_OPENAI_BASE_URL.");
			}
		}
		return baseUrl;
	}

	public static ModelProvider detectModelProvider(boolean isMicrosoftFoundry, boolean isGitHubModels,
			@Nullable String baseUrl, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAIServiceVersion) {

		if (isMicrosoftFoundry) {
			return ModelProvider.MICROSOFT_FOUNDRY; // Forced by the user
		}
		if (isGitHubModels) {
			return ModelProvider.GITHUB_MODELS; // Forced by the user
		}
		if (baseUrl != null) {
			if (baseUrl.endsWith("openai.azure.com") || baseUrl.endsWith("openai.azure.com/")
					|| baseUrl.endsWith("cognitiveservices.azure.com")
					|| baseUrl.endsWith("cognitiveservices.azure.com/")) {
				return ModelProvider.MICROSOFT_FOUNDRY;
			}
			else if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
				return ModelProvider.GITHUB_MODELS;
			}
		}
		if (azureDeploymentName != null || azureOpenAIServiceVersion != null) {
			return ModelProvider.MICROSOFT_FOUNDRY;
		}
		return ModelProvider.OPEN_AI;
	}

	static String calculateBaseUrl(@Nullable String baseUrl, ModelProvider modelProvider, @Nullable String modelName,
			@Nullable String azureDeploymentName) {

		if (modelProvider == ModelProvider.OPEN_AI) {
			if (baseUrl == null || baseUrl.isBlank()) {
				return OPENAI_URL;
			}
			return baseUrl;
		}
		else if (modelProvider == ModelProvider.GITHUB_MODELS) {
			if (baseUrl == null || baseUrl.isBlank()) {
				return GITHUB_MODELS_URL;
			}
			if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
				// To support GitHub Models for specific orgs
				return baseUrl;
			}
			return GITHUB_MODELS_URL;
		}
		else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
			if (baseUrl == null || baseUrl.isBlank()) {
				throw new IllegalArgumentException("Base URL must be provided for Microsoft Foundry.");
			}
			String tmpUrl = baseUrl;
			if (baseUrl.endsWith("/") || baseUrl.endsWith("?")) {
				tmpUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}
			// If the Azure deployment name is not configured, the model name will be used
			// by default by the OpenAI Java
			// SDK
			if (azureDeploymentName != null && !azureDeploymentName.equals(modelName)) {
				tmpUrl += "/openai/deployments/" + azureDeploymentName;
			}
			return tmpUrl;
		}
		else {
			throw new IllegalArgumentException("Unknown model provider: " + modelProvider);
		}
	}

	static Credential azureAuthentication() {
		try {
			return AzureInternalOpenAiSdkHelper.getAzureCredential();
		}
		catch (NoClassDefFoundError e) {
			throw new IllegalArgumentException("Microsoft Foundry was detected, but no credential was provided. "
					+ "If you want to use passwordless authentication, you need to add the Azure Identity library (groupId=`com.azure`, artifactId=`azure-identity`) to your classpath.");
		}
	}

	static @Nullable String detectApiKey(ModelProvider modelProvider) {
		if (modelProvider == ModelProvider.OPEN_AI && System.getenv(OPENAI_API_KEY) != null) {
			return System.getenv(OPENAI_API_KEY);
		}
		else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY && System.getenv(MICROSOFT_FOUNDRY_API_KEY) != null) {
			return System.getenv(MICROSOFT_FOUNDRY_API_KEY);
		}
		else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY && System.getenv(OPENAI_API_KEY) != null) {
			return System.getenv(OPENAI_API_KEY);
		}
		else if (modelProvider == ModelProvider.GITHUB_MODELS && System.getenv(GITHUB_TOKEN) != null) {
			return System.getenv(GITHUB_TOKEN);
		}
		return null;
	}

}
