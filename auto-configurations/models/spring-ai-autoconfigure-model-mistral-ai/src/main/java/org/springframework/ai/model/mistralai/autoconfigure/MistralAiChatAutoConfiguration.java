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

package org.springframework.ai.model.mistralai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.api.MistralAiApi;
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
 * Chat {@link AutoConfiguration Auto-configuration} for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 0.8.1
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.MISTRAL,
		matchIfMissing = true)
@ConditionalOnClass(MistralAiApi.class)
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class })
public class MistralAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MistralAiChatModel mistralAiChatModel(MistralAiCommonProperties commonProperties,
			MistralAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> mistralAiToolExecutionEligibilityPredicate) {

		var mistralAiApi = mistralAiApi(chatProperties.getApiKey(), commonProperties.getApiKey(),
				chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler);

		var chatModel = MistralAiChatModel.builder()
			.mistralAiApi(mistralAiApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(mistralAiToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	private MistralAiApi mistralAiApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resoledBaseUrl, "Mistral base URL must be set");

		return new MistralAiApi(resoledBaseUrl, resolvedApiKey, restClientBuilder, webClientBuilder,
				responseErrorHandler);
	}

}
