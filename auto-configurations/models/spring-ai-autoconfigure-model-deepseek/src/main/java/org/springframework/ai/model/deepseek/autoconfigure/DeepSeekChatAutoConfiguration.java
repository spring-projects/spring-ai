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

package org.springframework.ai.model.deepseek.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for DeepSeek Chat Model.
 *
 * @author Geng Rong
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass(DeepSeekApi.class)
@EnableConfigurationProperties({ DeepSeekConnectionProperties.class, DeepSeekChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.DEEPSEEK,
		matchIfMissing = true)
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class })
public class DeepSeekChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public DeepSeekChatModel deepSeekChatModel(DeepSeekConnectionProperties commonProperties,
			DeepSeekChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> deepseekToolExecutionEligibilityPredicate) {

		var deepSeekApi = deepSeekApi(chatProperties, commonProperties,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler);

		var chatModel = DeepSeekChatModel.builder()
			.deepSeekApi(deepSeekApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(deepseekToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	private DeepSeekApi deepSeekApi(DeepSeekChatProperties chatProperties,
			DeepSeekConnectionProperties commonProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl()
				: commonProperties.getBaseUrl();
		Assert.hasText(resolvedBaseUrl, "DeepSeek base URL must be set");

		String resolvedApiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey()
				: commonProperties.getApiKey();
		Assert.hasText(resolvedApiKey, "DeepSeek API key must be set");

		return DeepSeekApi.builder()
			.baseUrl(resolvedBaseUrl)
			.apiKey(new SimpleApiKey(resolvedApiKey))
			.completionsPath(chatProperties.getCompletionsPath())
			.betaPrefixPath(chatProperties.getBetaPrefixPath())
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

}
