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

package org.springframework.ai.autoconfigure.hunyuan;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.hunyuan.HunYuanChatModel;
import org.springframework.ai.hunyuan.api.HunYuanApi;
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

import java.util.List;

/**
 * {@link AutoConfiguration Auto-configuration} for HunYuan Chat Model.
 *
 * @author Guo Junyu
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ HunYuanCommonProperties.class, HunYuanChatProperties.class })
@ConditionalOnClass(HunYuanApi.class)
public class HunYuanAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = HunYuanChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public HunYuanChatModel hunyuanChatModel(HunYuanCommonProperties commonProperties,
			HunYuanChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackResolver functionCallbackResolver,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var hunyuanApi = hunyuanApi(chatProperties.getSecretId(), commonProperties.getSecretId(),
				chatProperties.getSecretKey(), commonProperties.getSecretKey(), chatProperties.getBaseUrl(),
				commonProperties.getBaseUrl(), restClientBuilderProvider.getIfAvailable(RestClient::builder),
				responseErrorHandler);

		var chatModel = new HunYuanChatModel(hunyuanApi, chatProperties.getOptions(), functionCallbackResolver,
				toolFunctionCallbacks, retryTemplate, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
	}

	private HunYuanApi hunyuanApi(String secretId, String commonSecretId, String secretKey, String commonSecretKey,
			String baseUrl, String commonBaseUrl, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		var resolvedSecretId = StringUtils.hasText(secretId) ? secretId : commonSecretId;
		var resolvedSecretKey = StringUtils.hasText(secretKey) ? secretKey : commonSecretKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedSecretId, "HunYuan SecretId must be set");
		Assert.hasText(resolvedSecretKey, "HunYuan SecretKey must be set");
		Assert.hasText(resoledBaseUrl, "HunYuan base URL must be set");

		return new HunYuanApi(resoledBaseUrl, resolvedSecretId, resolvedSecretKey, restClientBuilder,
				responseErrorHandler);
	}

}
