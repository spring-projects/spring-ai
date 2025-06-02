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

package org.springframework.ai.model.minimax.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * {@link AutoConfiguration Auto-configuration} for MiniMax Chat Model.
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
@AutoConfiguration(
		after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
				ToolCallingAutoConfiguration.class },
		beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@ConditionalOnClass(MiniMaxApi.class)
@EnableConfigurationProperties({ MiniMaxConnectionProperties.class, MiniMaxChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.MINIMAX,
		matchIfMissing = true)
public class MiniMaxChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MiniMaxChatModel miniMaxChatModel(MiniMaxConnectionProperties commonProperties,
			MiniMaxChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> openAiToolExecutionEligibilityPredicate) {

		var miniMaxApi = miniMaxApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder), responseErrorHandler);

		var chatModel = new MiniMaxChatModel(miniMaxApi, chatProperties.getOptions(), toolCallingManager, retryTemplate,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				openAiToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new));

		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

	private MiniMaxApi miniMaxApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "MiniMax base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "MiniMax API key must be set");

		return new MiniMaxApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

}
