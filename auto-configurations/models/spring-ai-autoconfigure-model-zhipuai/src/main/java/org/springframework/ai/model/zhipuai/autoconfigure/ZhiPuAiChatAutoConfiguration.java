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

package org.springframework.ai.model.zhipuai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Chat {@link AutoConfiguration Auto-configuration} for ZhiPuAI.
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
		ToolCallingAutoConfiguration.class })
@ConditionalOnClass(ZhiPuAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.ZHIPUAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ ZhiPuAiConnectionProperties.class, ZhiPuAiChatProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		ToolCallingAutoConfiguration.class })
public class ZhiPuAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ZhiPuAiChatModel zhiPuAiChatModel(ZhiPuAiConnectionProperties commonProperties,
			ZhiPuAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention, ToolCallingManager toolCallingManager,
			ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {

		var zhiPuAiApi = zhiPuAiApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var chatModel = new ZhiPuAiChatModel(zhiPuAiApi, chatProperties.getOptions(), toolCallingManager, retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new));

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	private ZhiPuAiApi zhiPuAiApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "ZhiPuAI base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "ZhiPuAI API key must be set");

		return new ZhiPuAiApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

}
