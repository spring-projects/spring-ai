/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.mistralai;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.1
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiEmbeddingProperties.class, MistralAiCommonProperties.class,
		MistralAiChatProperties.class })
@ConditionalOnClass(MistralAiApi.class)
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class MistralAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public MistralAiEmbeddingModel mistralAiEmbeddingModel(MistralAiCommonProperties commonProperties,
			MistralAiEmbeddingProperties embeddingProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var mistralAiApi = mistralAiApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var embeddingModel = new MistralAiEmbeddingModel(mistralAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public MistralAiChatModel mistralAiChatModel(MistralAiCommonProperties commonProperties,
			MistralAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackResolver functionCallbackResolver,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var mistralAiApi = mistralAiApi(chatProperties.getApiKey(), commonProperties.getApiKey(),
				chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var chatModel = new MistralAiChatModel(mistralAiApi, chatProperties.getOptions(), functionCallbackResolver,
				toolFunctionCallbacks, retryTemplate, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	private MistralAiApi mistralAiApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resoledBaseUrl, "Mistral base URL must be set");

		return new MistralAiApi(resoledBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
	}

}
