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
package org.springframework.ai.autoconfigure.openai;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.OpenAiAudioTranscriptionClient;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.ai.openai.OpenAiAudioSpeechClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class,
		OpenAiEmbeddingProperties.class, OpenAiImageProperties.class, OpenAiAudioTranscriptionProperties.class,
		OpenAiAudioSpeechProperties.class })
public class OpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiChatClient openAiChatClient(OpenAiConnectionProperties commonProperties,
			OpenAiChatProperties chatProperties, RestClient.Builder restClientBuilder,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackContext functionCallbackContext,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var openAiApi = openAiApi(commonProperties, chatProperties, restClientBuilder, responseErrorHandler);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new OpenAiChatClient(openAiApi, chatProperties.getOptions(), functionCallbackContext, retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var openAiApi = openAiApi(commonProperties, embeddingProperties, restClientBuilder, responseErrorHandler);

		return new OpenAiEmbeddingClient(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	private <T extends OpenAiParentProperties> OpenAiApi openAiApi(OpenAiConnectionProperties commonProperties,
			T specificProperties, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				specificProperties);
		RestClient.Builder overrideRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		return new OpenAiApi(overridenCommonProperties.getBaseUrl(), overridenCommonProperties.getApiKey(),
				overrideRestClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiImageClient openAiImageClient(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				imageProperties);
		RestClient.Builder overrideRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		var openAiImageApi = new OpenAiImageApi(overridenCommonProperties.getBaseUrl(),
				overridenCommonProperties.getApiKey(), overrideRestClientBuilder, responseErrorHandler);

		return new OpenAiImageClient(openAiImageApi, imageProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiAudioTranscriptionClient openAiAudioTranscriptionClient(OpenAiConnectionProperties commonProperties,
			OpenAiAudioTranscriptionProperties transcriptionProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				transcriptionProperties);
		RestClient.Builder overrideRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		var openAiAudioApi = new OpenAiAudioApi(overridenCommonProperties.getBaseUrl(),
				overridenCommonProperties.getApiKey(), overrideRestClientBuilder, responseErrorHandler);

		return new OpenAiAudioTranscriptionClient(openAiAudioApi, transcriptionProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiAudioSpeechClient openAiAudioSpeechClient(OpenAiConnectionProperties commonProperties,
			OpenAiAudioSpeechProperties speechProperties, ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(speechProperties.getApiKey()) ? speechProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(speechProperties.getBaseUrl()) ? speechProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "OpenAI API key must be set");
		Assert.hasText(baseUrl, "OpenAI base URL must be set");

		var openAiAudioApi = new OpenAiAudioApi(baseUrl, apiKey, RestClient.builder(), responseErrorHandler);

		OpenAiAudioSpeechClient openAiSpeechClient = new OpenAiAudioSpeechClient(openAiAudioApi,
				speechProperties.getOptions());

		return openAiSpeechClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

	private static <T extends OpenAiParentProperties> OpenAiConnectionProperties checkAndOverrideProperties(
			OpenAiConnectionProperties commonProperties, T specificProperties) {

		String apiKey = StringUtils.hasText(specificProperties.getApiKey()) ? specificProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(specificProperties.getBaseUrl()) ? specificProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Duration readTimeout = specificProperties.getReadTimeout() != null ? specificProperties.getReadTimeout()
				: commonProperties.getReadTimeout();

		Assert.hasText(apiKey, "OpenAI API key must be set");
		Assert.hasText(baseUrl, "OpenAI base URL must be set");
		Assert.notNull(readTimeout, "OpenAI base read timeout must be set");

		OpenAiConnectionProperties overridenCommonProperties = new OpenAiConnectionProperties();
		overridenCommonProperties.setApiKey(apiKey);
		overridenCommonProperties.setBaseUrl(baseUrl);
		overridenCommonProperties.setReadTimeout(readTimeout);

		return overridenCommonProperties;

	}

	private static RestClient.Builder overrideRestClientBuilder(RestClient.Builder restClientBuilder,
			OpenAiConnectionProperties overridenCommonProperties) {
		ClientHttpRequestFactorySettings requestFactorySettings = new ClientHttpRequestFactorySettings(
				Duration.ofHours(1l), overridenCommonProperties.getReadTimeout(), SslBundle.of(null));
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(requestFactorySettings);
		return restClientBuilder.clone().requestFactory(requestFactory);
	}

}
