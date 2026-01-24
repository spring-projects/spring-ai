/*
 * Copyright 2023-2026 the original author or authors.
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

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.mistralai.moderation.MistralAiModerationModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Moderation {@link AutoConfiguration Auto-configuration} for Mistral AI.
 *
 * @author Ricken Bazolo
 * @author Yanming Zhou
 * @author Nicolas Krier
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
		WebClientAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiModerationProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.MODERATION_MODEL, havingValue = SpringAIModels.MISTRAL,
		matchIfMissing = true)
@ConditionalOnClass(MistralAiApi.class)
public class MistralAiModerationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	MistralAiModerationModel mistralAiModerationModel(MistralAiCommonProperties commonProperties,
			MistralAiModerationProperties moderationProperties, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
		var mistralAiModerationApi = mistralAiModerationApi(commonProperties, moderationProperties,
				restClientBuilderProvider, responseErrorHandler);

		return MistralAiModerationModel.builder()
			.mistralAiModerationApi(mistralAiModerationApi)
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.options(moderationProperties.getOptions())
			.build();
	}

	private static MistralAiModerationApi mistralAiModerationApi(MistralAiCommonProperties commonProperties,
			MistralAiModerationProperties moderationProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
		var resolvedApiKey = MistralAiPropertiesUtils.resolveApiKey(commonProperties, moderationProperties);
		var resolvedBaseUrl = MistralAiPropertiesUtils.resolveBaseUrl(commonProperties, moderationProperties);

		return MistralAiModerationApi.builder()
			.baseUrl(resolvedBaseUrl)
			.apiKey(resolvedApiKey)
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();
	}

}
