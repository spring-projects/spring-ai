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

package org.springframework.ai.openaiofficial.setup;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.Duration.ofSeconds;

/**
 * Helps configure the OpenAI Java SDK, depending on the platform used. This code is
 * inspired by LangChain4j's
 * `dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper` class, which is
 * coded by the same author (Julien Dubois, from Microsoft).
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialSetup {

	static final String OPENAI_URL = "https://api.openai.com/v1";
	static final String GITHUB_MODELS_URL = "https://models.inference.ai.azure.com";
	static final String GITHUB_TOKEN = "GITHUB_TOKEN";
	static final String DEFAULT_USER_AGENT = "spring-ai-openai-official";

	private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialSetup.class);

	private static final Duration DEFAULT_DURATION = ofSeconds(60);

	private static final int DEFAULT_MAX_RETRIES = 3;

	public enum ModelHost {

		OPENAI, AZURE_OPENAI, GITHUB_MODELS

	}

	public static OpenAIClient setupSyncClient(String baseUrl, String apiKey, Credential credential,
			String azureDeploymentName, AzureOpenAIServiceVersion azureOpenAiServiceVersion, String organizationId,
			boolean isAzure, boolean isGitHubModels, String modelName, Duration timeout, Integer maxRetries,
			Proxy proxy, Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelHost = detectModelHost(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);
		if (timeout == null) {
			timeout = DEFAULT_DURATION;
		}
		if (maxRetries == null) {
			maxRetries = DEFAULT_MAX_RETRIES;
		}

		OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();
		builder
			.baseUrl(calculateBaseUrl(baseUrl, modelHost, modelName, azureDeploymentName, azureOpenAiServiceVersion));

		Credential calculatedCredential = calculateCredential(modelHost, apiKey, credential);
		String calculatedApiKey = calculateApiKey(modelHost, apiKey);
		if (calculatedCredential == null && calculatedApiKey == null) {
			throw new IllegalArgumentException("Either apiKey or credential must be set to authenticate");
		}
		else if (calculatedCredential != null) {
			builder.credential(calculatedCredential);
		}
		else {
			builder.apiKey(calculatedApiKey);
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
	public static OpenAIClientAsync setupAsyncClient(String baseUrl, String apiKey, Credential credential,
			String azureDeploymentName, AzureOpenAIServiceVersion azureOpenAiServiceVersion, String organizationId,
			boolean isAzure, boolean isGitHubModels, String modelName, Duration timeout, Integer maxRetries,
			Proxy proxy, Map<String, String> customHeaders) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelHost = detectModelHost(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);
		if (timeout == null) {
			timeout = DEFAULT_DURATION;
		}
		if (maxRetries == null) {
			maxRetries = DEFAULT_MAX_RETRIES;
		}

		OpenAIOkHttpClientAsync.Builder builder = OpenAIOkHttpClientAsync.builder();
		builder
			.baseUrl(calculateBaseUrl(baseUrl, modelHost, modelName, azureDeploymentName, azureOpenAiServiceVersion));

		Credential calculatedCredential = calculateCredential(modelHost, apiKey, credential);
		String calculatedApiKey = calculateApiKey(modelHost, apiKey);
		if (calculatedCredential == null && calculatedApiKey == null) {
			throw new IllegalArgumentException("Either apiKey or credential must be set to authenticate");
		}
		else if (calculatedCredential != null) {
			builder.credential(calculatedCredential);
		}
		else {
			builder.apiKey(calculatedApiKey);
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

	static String detectBaseUrlFromEnv(String baseUrl) {
		if (baseUrl == null) {
			var openAiBaseUrl = System.getenv("OPENAI_BASE_URL");
			if (openAiBaseUrl != null) {
				baseUrl = openAiBaseUrl;
				logger.debug("OpenAI Base URL detected from environment variable OPENAI_BASE_URL.");
			}
			var azureOpenAiBaseUrl = System.getenv("AZURE_OPENAI_BASE_URL");
			if (azureOpenAiBaseUrl != null) {
				baseUrl = azureOpenAiBaseUrl;
				logger.debug("Azure OpenAI Base URL detected from environment variable AZURE_OPENAI_BASE_URL.");
			}
		}
		return baseUrl;
	}

	static ModelHost detectModelHost(boolean isAzure, boolean isGitHubModels, String baseUrl,
			String azureDeploymentName, AzureOpenAIServiceVersion azureOpenAIServiceVersion) {

		if (isAzure) {
			return ModelHost.AZURE_OPENAI; // Forced by the user
		}
		if (isGitHubModels) {
			return ModelHost.GITHUB_MODELS; // Forced by the user
		}
		if (baseUrl != null) {
			if (baseUrl.endsWith("openai.azure.com") || baseUrl.endsWith("openai.azure.com/")
					|| baseUrl.endsWith("cognitiveservices.azure.com")
					|| baseUrl.endsWith("cognitiveservices.azure.com/")) {
				return ModelHost.AZURE_OPENAI;
			}
			else if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
				return ModelHost.GITHUB_MODELS;
			}
		}
		if (azureDeploymentName != null || azureOpenAIServiceVersion != null) {
			return ModelHost.AZURE_OPENAI;
		}
		return ModelHost.OPENAI;
	}

	static String calculateBaseUrl(String baseUrl, ModelHost modelHost, String modelName, String azureDeploymentName,
			AzureOpenAIServiceVersion azureOpenAiServiceVersion) {

		if (modelHost == ModelHost.OPENAI) {
			if (baseUrl == null || baseUrl.isBlank()) {
				return OPENAI_URL;
			}
			return baseUrl;
		}
		else if (modelHost == ModelHost.GITHUB_MODELS) {
			return GITHUB_MODELS_URL;
		}
		else if (modelHost == ModelHost.AZURE_OPENAI) {
			// Using Azure OpenAI
			String tmpUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
			// If the Azure deployment name is not configured, the model name will be used
			// by default by the OpenAI Java
			// SDK
			if (azureDeploymentName != null && !azureDeploymentName.equals(modelName)) {
				tmpUrl += "/openai/deployments/" + azureDeploymentName;
			}
			if (azureOpenAiServiceVersion != null) {
				tmpUrl += "?api-version=" + azureOpenAiServiceVersion.value();
			}
			return tmpUrl;
		}
		else {
			throw new IllegalArgumentException("Unknown model host: " + modelHost);
		}
	}

	static Credential calculateCredential(ModelHost modelHost, String apiKey, Credential credential) {
		if (apiKey != null) {
			if (modelHost == ModelHost.AZURE_OPENAI) {
				return AzureApiKeyCredential.create(apiKey);
			}
		}
		else if (credential != null) {
			return credential;
		}
		else if (modelHost == ModelHost.AZURE_OPENAI) {
			try {
				return AzureInternalOpenAiOfficialHelper.getAzureCredential();
			}
			catch (NoClassDefFoundError e) {
				throw new IllegalArgumentException("Azure OpenAI was detected, but no credential was provided. "
						+ "If you want to use passwordless authentication, you need to add the Azure Identity library (groupId=`com.azure`, artifactId=`azure-identity`) to your classpath.");
			}
		}
		return null;
	}

	static String calculateApiKey(ModelHost modelHost, String apiKey) {
		if (apiKey == null) {
			var openAiKey = System.getenv("OPENAI_API_KEY");
			if (openAiKey != null) {
				apiKey = openAiKey;
				logger.debug("OpenAI API Key detected from environment variable OPENAI_API_KEY.");
			}
			var azureOpenAiKey = System.getenv("AZURE_OPENAI_KEY");
			if (azureOpenAiKey != null) {
				apiKey = azureOpenAiKey;
				logger.debug("Azure OpenAI Key detected from environment variable AZURE_OPENAI_KEY.");
			}
		}
		if (modelHost != ModelHost.AZURE_OPENAI && apiKey != null) {
			return apiKey;
		}
		else if (modelHost == ModelHost.GITHUB_MODELS && System.getenv(GITHUB_TOKEN) != null) {
			return System.getenv(GITHUB_TOKEN);
		}
		return null;
	}

}
