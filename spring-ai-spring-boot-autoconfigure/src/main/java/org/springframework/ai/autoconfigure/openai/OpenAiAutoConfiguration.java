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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
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

		var openAiApi = openAiApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(), restClientBuilder, responseErrorHandler);

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

		var openAiApi = openAiApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties.getApiKey(), restClientBuilder, responseErrorHandler);

		return new OpenAiEmbeddingClient(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	private OpenAiApi openAiApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "OpenAI base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "OpenAI API key must be set");

		return new OpenAiApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiImageClient openAiImageClient(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "OpenAI API key must be set");
		Assert.hasText(baseUrl, "OpenAI base URL must be set");

		var openAiImageApi = new OpenAiImageApi(baseUrl, apiKey, restClientBuilder, responseErrorHandler);

		return new OpenAiImageClient(openAiImageApi, imageProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiAudioTranscriptionClient openAiAudioTranscriptionClient(OpenAiConnectionProperties commonProperties,
			OpenAiAudioTranscriptionProperties transcriptionProperties, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(transcriptionProperties.getApiKey()) ? transcriptionProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(transcriptionProperties.getBaseUrl())
				? transcriptionProperties.getBaseUrl() : commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "OpenAI API key must be set");
		Assert.hasText(baseUrl, "OpenAI base URL must be set");

		var openAiAudioApi = new OpenAiAudioApi(baseUrl, apiKey, RestClient.builder(), responseErrorHandler);

		OpenAiAudioTranscriptionClient openAiChatClient = new OpenAiAudioTranscriptionClient(openAiAudioApi,
				transcriptionProperties.getOptions(), retryTemplate);

		return openAiChatClient;
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

}
