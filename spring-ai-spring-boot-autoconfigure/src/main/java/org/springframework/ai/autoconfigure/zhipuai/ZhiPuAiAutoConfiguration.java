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

package org.springframework.ai.autoconfigure.zhipuai;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for ZhiPuAI.
 *
 * @author Geng Rong
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(ZhiPuAiApi.class)
@EnableConfigurationProperties({ ZhiPuAiConnectionProperties.class, ZhiPuAiChatProperties.class,
		ZhiPuAiEmbeddingProperties.class, ZhiPuAiImageProperties.class })
public class ZhiPuAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ZhiPuAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public ZhiPuAiChatModel zhiPuAiChatModel(ZhiPuAiConnectionProperties commonProperties,
			ZhiPuAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackResolver functionCallbackResolver,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var zhiPuAiApi = zhiPuAiApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var chatModel = new ZhiPuAiChatModel(zhiPuAiApi, chatProperties.getOptions(), functionCallbackResolver,
				toolFunctionCallbacks, retryTemplate, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ZhiPuAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public ZhiPuAiEmbeddingModel zhiPuAiEmbeddingModel(ZhiPuAiConnectionProperties commonProperties,
			ZhiPuAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var zhiPuAiApi = zhiPuAiApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties.getApiKey(), restClientBuilder, responseErrorHandler);

		var embeddingModel = new ZhiPuAiEmbeddingModel(zhiPuAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private ZhiPuAiApi zhiPuAiApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "ZhiPuAI base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "ZhiPuAI API key must be set");

		return new ZhiPuAiApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ZhiPuAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public ZhiPuAiImageModel zhiPuAiImageModel(ZhiPuAiConnectionProperties commonProperties,
			ZhiPuAiImageProperties imageProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "ZhiPuAI API key must be set");
		Assert.hasText(baseUrl, "ZhiPuAI base URL must be set");

		var zhiPuAiImageApi = new ZhiPuAiImageApi(baseUrl, apiKey, restClientBuilder, responseErrorHandler);

		return new ZhiPuAiImageModel(zhiPuAiImageApi, imageProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
	}

}
