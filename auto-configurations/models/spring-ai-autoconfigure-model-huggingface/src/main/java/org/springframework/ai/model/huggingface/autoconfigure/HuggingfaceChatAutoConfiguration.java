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

package org.springframework.ai.model.huggingface.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for HuggingFace Chat Model.
 *
 * @author Mark Pollack
 * @author Josh Long
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Myeongdeok Kang
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, HuggingfaceApiAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass(HuggingfaceChatModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.HUGGINGFACE,
		matchIfMissing = true)
@EnableConfigurationProperties({ HuggingfaceConnectionProperties.class, HuggingfaceChatProperties.class })
public class HuggingfaceChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "huggingfaceChatApi")
	@ConditionalOnProperty(prefix = HuggingfaceChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public HuggingfaceApi huggingfaceChatApi(HuggingfaceConnectionDetails connectionDetails,
			HuggingfaceChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider) {

		String apiKey = connectionDetails.getApiKey();
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"HuggingFace API key must be set. Please configure spring.ai.huggingface.api-key");
		}

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		ResponseErrorHandler responseErrorHandler = responseErrorHandlerProvider.getIfAvailable(() -> null);

		HuggingfaceApi.Builder apiBuilder = HuggingfaceApi.builder()
			.baseUrl(chatProperties.getUrl())
			.apiKey(apiKey)
			.restClientBuilder(restClientBuilder);

		if (responseErrorHandler != null) {
			apiBuilder.responseErrorHandler(responseErrorHandler);
		}

		return apiBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = HuggingfaceChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public HuggingfaceChatModel huggingfaceChatModel(@Qualifier("huggingfaceChatApi") HuggingfaceApi huggingfaceApi,
			HuggingfaceChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention, RetryTemplate retryTemplate,
			ObjectProvider<ToolExecutionEligibilityPredicate> huggingfaceToolExecutionEligibilityPredicate) {

		var chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(huggingfaceApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(huggingfaceToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.retryTemplate(retryTemplate)
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
