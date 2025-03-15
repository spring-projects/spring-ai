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

package org.springframework.ai.autoconfigure.openai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.jetbrains.annotations.NotNull;

import org.springframework.ai.autoconfigure.chat.model.ToolCallingAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Christian Tzolov
 * @author Stefan Vassilev
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class,
		OpenAiEmbeddingProperties.class, OpenAiImageProperties.class, OpenAiAudioTranscriptionProperties.class,
		OpenAiAudioSpeechProperties.class, OpenAiModerationProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class })
public class OpenAiAutoConfiguration {

	private static @NotNull ResolvedConnectionProperties resolveConnectionProperties(
			OpenAiParentProperties commonProperties, OpenAiParentProperties modelProperties, String modelType) {

		String baseUrl = StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl();
		String apiKey = StringUtils.hasText(modelProperties.getApiKey()) ? modelProperties.getApiKey()
				: commonProperties.getApiKey();
		String projectId = StringUtils.hasText(modelProperties.getProjectId()) ? modelProperties.getProjectId()
				: commonProperties.getProjectId();
		String organizationId = StringUtils.hasText(modelProperties.getOrganizationId())
				? modelProperties.getOrganizationId() : commonProperties.getOrganizationId();

		Map<String, List<String>> connectionHeaders = new HashMap<>();
		if (StringUtils.hasText(projectId)) {
			connectionHeaders.put("OpenAI-Project", List.of(projectId));
		}
		if (StringUtils.hasText(organizationId)) {
			connectionHeaders.put("OpenAI-Organization", List.of(organizationId));
		}

		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the connection property: spring.ai.openai.base-url or spring.ai.openai."
						+ modelType + ".base-url property.");
		Assert.hasText(apiKey,
				"OpenAI API key must be set. Use the connection property: spring.ai.openai.api-key or spring.ai.openai."
						+ modelType + ".api-key property.");

		return new ResolvedConnectionProperties(baseUrl, apiKey, CollectionUtils.toMultiValueMap(connectionHeaders));
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiChatModel openAiChatModel(OpenAiConnectionProperties commonProperties,
			OpenAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var openAiApi = openAiApi(chatProperties, commonProperties,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler, "chat");

		var chatModel = OpenAiChatModel.builder()
			.openAiApi(openAiApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var openAiApi = openAiApi(embeddingProperties, commonProperties,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler, "embedding");

		var embeddingModel = new OpenAiEmbeddingModel(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private OpenAiApi openAiApi(OpenAiChatProperties chatProperties, OpenAiConnectionProperties commonProperties,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler, String modelType) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, chatProperties,
				modelType);

		return OpenAiApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.completionsPath(chatProperties.getCompletionsPath())
			.embeddingsPath(OpenAiEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH)
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

	private OpenAiApi openAiApi(OpenAiEmbeddingProperties embeddingProperties,
			OpenAiConnectionProperties commonProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler, String modelType) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, embeddingProperties,
				modelType);

		return OpenAiApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.completionsPath(OpenAiChatProperties.DEFAULT_COMPLETIONS_PATH)
			.embeddingsPath(embeddingProperties.getEmbeddingsPath())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiImageModel openAiImageModel(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ImageModelObservationConvention> observationConvention) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, imageProperties, "image");

		var openAiImageApi = OpenAiImageApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
		var imageModel = new OpenAiImageModel(openAiImageApi, imageProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(imageModel::setObservationConvention);

		return imageModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiAudioTranscriptionProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel(OpenAiConnectionProperties commonProperties,
			OpenAiAudioTranscriptionProperties transcriptionProperties, RetryTemplate retryTemplate,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ResponseErrorHandler responseErrorHandler) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, transcriptionProperties,
				"transcription");

		var openAiAudioApi = OpenAiAudioApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();

		return new OpenAiAudioTranscriptionModel(openAiAudioApi, transcriptionProperties.getOptions(), retryTemplate);

	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiModerationModel openAiModerationClient(OpenAiConnectionProperties commonProperties,
			OpenAiModerationProperties moderationProperties, RetryTemplate retryTemplate,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider, ResponseErrorHandler responseErrorHandler) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, moderationProperties,
				"moderation");

		var openAiModerationApi = OpenAiModerationApi.builder()
			.baseUrl(resolved.baseUrl)
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
		return new OpenAiModerationModel(openAiModerationApi, retryTemplate)
			.withDefaultOptions(moderationProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiAudioSpeechProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiAudioSpeechModel openAiAudioSpeechClient(OpenAiConnectionProperties commonProperties,
			OpenAiAudioSpeechProperties speechProperties, RetryTemplate retryTemplate,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ResponseErrorHandler responseErrorHandler) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, speechProperties,
				"speach");

		var openAiAudioApi = OpenAiAudioApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();

		return new OpenAiAudioSpeechModel(openAiAudioApi, speechProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
	}

	private record ResolvedConnectionProperties(String baseUrl, String apiKey, MultiValueMap<String, String> headers) {

	}

}
