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
package org.springframework.ai.autoconfigure.qianfan;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.qianfan.QianFanChatModel;
import org.springframework.ai.qianfan.QianFanEmbeddingModel;
import org.springframework.ai.qianfan.QianFanImageModel;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.qianfan.api.QianFanImageApi;
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
 * @author Geng Rong
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(QianFanApi.class)
@EnableConfigurationProperties({ QianFanConnectionProperties.class, QianFanChatProperties.class,
		QianFanEmbeddingProperties.class, QianFanImageProperties.class })
public class QianFanAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = QianFanChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public QianFanChatModel qianFanChatModel(QianFanConnectionProperties commonProperties,
			QianFanChatProperties chatProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var qianFanApi = qianFanApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(), chatProperties.getSecretKey(),
				commonProperties.getSecretKey(), restClientBuilder, responseErrorHandler);

		var chatModel = new QianFanChatModel(qianFanApi, chatProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = QianFanEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public QianFanEmbeddingModel qianFanEmbeddingModel(QianFanConnectionProperties commonProperties,
			QianFanEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var qianFanApi = qianFanApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties.getApiKey(), embeddingProperties.getSecretKey(),
				commonProperties.getSecretKey(), restClientBuilder, responseErrorHandler);

		var embeddingModel = new QianFanEmbeddingModel(qianFanApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = QianFanImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public QianFanImageModel qianFanImageModel(QianFanConnectionProperties commonProperties,
			QianFanImageProperties imageProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String secretKey = StringUtils.hasText(imageProperties.getSecretKey()) ? imageProperties.getSecretKey()
				: commonProperties.getSecretKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "QianFan API key must be set.  Use the property: spring.ai.qianfan.api-key");
		Assert.hasText(secretKey, "QianFan secret key must be set.  Use the property: spring.ai.qianfan.secret-key");
		Assert.hasText(baseUrl, "QianFan base URL must be set.  Use the property: spring.ai.qianfan.base-url");

		var qianFanImageApi = new QianFanImageApi(baseUrl, apiKey, secretKey, restClientBuilder, responseErrorHandler);

		return new QianFanImageModel(qianFanImageApi, imageProperties.getOptions(), retryTemplate);
	}

	private QianFanApi qianFanApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			String secretKey, String commonSecretKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "QianFan base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "QianFan API key must be set");

		String resolvedSecretKey = StringUtils.hasText(secretKey) ? secretKey : commonSecretKey;
		Assert.hasText(resolvedSecretKey, "QianFan Secret key must be set");

		return new QianFanApi(resolvedBaseUrl, resolvedApiKey, resolvedSecretKey, restClientBuilder,
				responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
