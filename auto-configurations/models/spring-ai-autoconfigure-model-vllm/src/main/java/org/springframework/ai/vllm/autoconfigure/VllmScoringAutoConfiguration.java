/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vllm.autoconfigure;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vllm.VllmScoringModel;
import org.springframework.ai.vllm.api.VllmScoringApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for vLLM Scoring Model.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(VllmScoringApi.class)
@EnableConfigurationProperties(VllmScoringProperties.class)
@ConditionalOnProperty(prefix = VllmScoringProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class VllmScoringAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VllmScoringModel vllmScoringModel(final VllmScoringProperties properties,
			final ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			final ObjectProvider<RetryTemplate> retryTemplate,
			final ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		Assert.hasText(properties.getBaseUrl(), "vLLM base URL must be set");

		var vllmScoringApi = VllmScoringApi.builder()
			.baseUrl(properties.getBaseUrl())
			.apiKey(properties.getApiKey())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();

		return VllmScoringModel.builder()
			.vllmScoringApi(vllmScoringApi)
			.options(properties.getOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
	}

}
