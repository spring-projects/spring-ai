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

package org.springframework.ai.openai.setup;

import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.Credential;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.OkHttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

/**
 * Helps configure the OpenAI Java SDK, depending on the platform used. This code is
 * inspired by LangChain4j's
 * `dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper` class, which is
 * coded by the same author (Julien Dubois, from Microsoft).
 *
 * @author Julien Dubois
 * @author Thomas Vitale
 * @author Jewoo Shin
 */
public final class OpenAiSetup {

	static final String OPENAI_URL = "https://api.openai.com/v1";
	static final String OPENAI_API_KEY = "OPENAI_API_KEY";
	static final String MICROSOFT_FOUNDRY_API_KEY = "MICROSOFT_FOUNDRY_API_KEY";
	static final String GITHUB_MODELS_URL = "https://models.github.ai/inference";
	static final String GITHUB_TOKEN = "GITHUB_TOKEN";
	static final String DEFAULT_USER_AGENT = "spring-ai-openai";

	/**
	 * Placeholder API key used internally when no-auth mode is requested (empty apiKey /
	 * {@link org.springframework.ai.model.NoopApiKey}). The OpenAI Java SDK requires a
	 * non-empty string to pass its credential validation at both construction and
	 * per-request time — an empty string causes an {@link IllegalStateException} in
	 * {@code securityHeaders()}. The OkHttp interceptor registered in
	 * {@link #buildNoAuthClientOptions} guarantees this placeholder value is stripped
	 * from the {@code Authorization} header before any request leaves the JVM.
	 */
	private static final String NO_AUTH_PLACEHOLDER_KEY = "no-auth-placeholder";

	private static final Log logger = LogFactory.getLog(OpenAiSetup.class);

	private OpenAiSetup() {
	}

	public enum ModelProvider {

		OPEN_AI, MICROSOFT_FOUNDRY, GITHUB_MODELS

	}

	/**
	 * Sets up a synchronous OpenAI client.
	 * <p>
	 * When {@code apiKey} is an empty string, the client is configured in no-auth mode:
	 * no {@code Authorization} header is sent with any request. This is useful for
	 * connecting to custom OpenAI-compatible servers that use cookie-based or other
	 * non-bearer-token authentication. To enable this mode, set
	 * {@code spring.ai.openai.api-key=} (empty value) in your application properties, or
	 * pass a {@link org.springframework.ai.model.NoopApiKey} via
	 * {@link org.springframework.ai.openai.AbstractOpenAiOptions.AbstractBuilder#apiKey(org.springframework.ai.model.ApiKey)}.
	 */
	public static OpenAIClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			boolean isAzure, boolean isGitHubModels, @Nullable String modelName, Duration timeout, int maxRetries,
			@Nullable Proxy proxy, @Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelProvider = detectModelProvider(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);

		String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
		if (calculatedApiKey != null && calculatedApiKey.isEmpty()) {
			// No-auth mode: build the client directly via ClientOptions so we can inject
			// a custom HttpClient that strips the Authorization header before sending
			// requests.
			return new OpenAIClientImpl(buildNoAuthClientOptions(baseUrl, modelProvider, modelName, azureDeploymentName,
					azureOpenAiServiceVersion, organizationId, timeout, maxRetries, proxy, customHeaders,
					observationRegistry, meterRegistry, httpClientCustomizers));
		}

		ClientOptions opts = buildClientOptions(baseUrl, modelProvider, modelName, azureDeploymentName,
				azureOpenAiServiceVersion, organizationId, timeout, maxRetries, proxy, customHeaders, calculatedApiKey,
				credential, observationRegistry, meterRegistry, httpClientCustomizers);
		return new OpenAIClientImpl(opts);
	}

	public static OpenAIClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Credential credential, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			boolean isAzure, boolean isGitHubModels, @Nullable String modelName, Duration timeout, int maxRetries,
			@Nullable Proxy proxy, @Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		baseUrl = detectBaseUrlFromEnv(baseUrl);
		var modelProvider = detectModelProvider(isAzure, isGitHubModels, baseUrl, azureDeploymentName,
				azureOpenAiServiceVersion);

		String calculatedApiKey = apiKey != null ? apiKey : detectApiKey(modelProvider);
		if (calculatedApiKey != null && calculatedApiKey.isEmpty()) {
			// No-auth mode: build the client directly via ClientOptions so we can inject
			// a custom HttpClient that strips the Authorization header before
			// sending requests.
			return new OpenAIClientAsyncImpl(buildNoAuthClientOptions(baseUrl, modelProvider, modelName,
					azureDeploymentName, azureOpenAiServiceVersion, organizationId, timeout, maxRetries, proxy,
					customHeaders, observationRegistry, meterRegistry, httpClientCustomizers));
		}

		ClientOptions opts = buildClientOptions(baseUrl, modelProvider, modelName, azureDeploymentName,
				azureOpenAiServiceVersion, organizationId, timeout, maxRetries, proxy, customHeaders, calculatedApiKey,
				credential, observationRegistry, meterRegistry, httpClientCustomizers);
		return new OpenAIClientAsyncImpl(opts);
	}

	private static ClientOptions buildClientOptions(@Nullable String baseUrl, ModelProvider modelProvider,
			@Nullable String modelName, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			Duration timeout, int maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable String calculatedApiKey, @Nullable Credential credential, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		SpringAiOpenAiHttpClient.Builder httpBuilder = SpringAiOpenAiHttpClient.builder()
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry)
			.timeout(timeout)
			.proxy(proxy);

		for (OpenAiHttpClientBuilderCustomizer customizer : httpClientCustomizers) {
			customizer.customize(httpBuilder);
		}

		String calculatedBaseUrl = calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName);

		ClientOptions.Builder clientOptions = ClientOptions.builder()
			.httpClient(httpBuilder.build())
			.baseUrl(calculatedBaseUrl)
			.organization(organizationId)
			.timeout(timeout)
			.maxRetries(maxRetries)
			.putHeader("User-Agent", DEFAULT_USER_AGENT);

		if (calculatedApiKey != null) {
			if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
				clientOptions.credential(AzureApiKeyCredential.create(calculatedApiKey));
			}
			else {
				clientOptions.apiKey(calculatedApiKey);
			}
		}
		else {
			if (credential != null) {
				clientOptions.credential(credential);
			}
			else if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
				clientOptions.credential(azureAuthentication());
			}
		}

		if (azureOpenAiServiceVersion != null) {
			clientOptions.azureServiceVersion(azureOpenAiServiceVersion);
		}
		if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
			clientOptions.azureUrlPathMode(resolveAzureUrlPathMode(calculatedBaseUrl));
		}
		if (customHeaders != null) {
			clientOptions.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		return clientOptions.build();
	}

	/**
	 * Builds a {@link ClientOptions} instance with a custom {@link OkHttpClient} that
	 * removes the {@code Authorization} header from every outgoing request. Used when an
	 * empty API key is provided to signal no-auth mode.
	 */
	private static ClientOptions buildNoAuthClientOptions(@Nullable String baseUrl, ModelProvider modelProvider,
			@Nullable String modelName, @Nullable String azureDeploymentName,
			@Nullable AzureOpenAIServiceVersion azureOpenAiServiceVersion, @Nullable String organizationId,
			Duration timeout, int maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		SpringAiOpenAiHttpClient.Builder httpBuilder = SpringAiOpenAiHttpClient.builder()
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry)
			.timeout(timeout)
			.proxy(proxy);

		// No API Key defined, so remove the mandatory "Authorization" header.
		httpBuilder
			.interceptor(chain -> chain.proceed(chain.request().newBuilder().removeHeader("Authorization").build()));

		for (OpenAiHttpClientBuilderCustomizer customizer : httpClientCustomizers) {
			customizer.customize(httpBuilder);
		}

		String calculatedBaseUrl = calculateBaseUrl(baseUrl, modelProvider, modelName, azureDeploymentName);

		ClientOptions.Builder clientOptions = ClientOptions.builder()
			.httpClient(httpBuilder.build())
			.apiKey(NO_AUTH_PLACEHOLDER_KEY)
			.baseUrl(calculatedBaseUrl)
			.organization(organizationId)
			.timeout(timeout)
			.maxRetries(maxRetries)
			.putHeader("User-Agent", DEFAULT_USER_AGENT);

		if (azureOpenAiServiceVersion != null) {
			clientOptions.azureServiceVersion(azureOpenAiServiceVersion);
		}
		if (modelProvider == ModelProvider.MICROSOFT_FOUNDRY) {
			clientOptions.azureUrlPathMode(resolveAzureUrlPathMode(calculatedBaseUrl));
		}
		if (customHeaders != null) {
			clientOptions.putAllHeaders(customHeaders.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue()))));
		}

		return clientOptions.build();
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
			return tmpUrl;
		}
		else {
			throw new IllegalArgumentException("Unknown model provider: " + modelProvider);
		}
	}

	static Credential azureAuthentication() {
		try {
			return AzureInternalOpenAiHelper.getAzureCredential();
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

	static AzureUrlPathMode resolveAzureUrlPathMode(@Nullable String baseUrl) {
		return (baseUrl != null && baseUrl.trim().endsWith("/openai/v1")) ? AzureUrlPathMode.UNIFIED
				: AzureUrlPathMode.LEGACY;
	}

}
