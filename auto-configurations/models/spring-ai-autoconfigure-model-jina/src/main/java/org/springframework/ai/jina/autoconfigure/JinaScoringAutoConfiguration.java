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

package org.springframework.ai.jina.autoconfigure;

import org.springframework.ai.jina.JinaScoringModel;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.retry.RetryUtils;
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
 * {@link AutoConfiguration Auto-configuration} for Jina AI Scoring Model.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(JinaScoringApi.class)
@EnableConfigurationProperties({ JinaScoringProperties.class })
@ConditionalOnProperty(prefix = JinaScoringProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class JinaScoringAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = JinaScoringProperties.CONFIG_PREFIX, name = "api-key")
	public JinaScoringModel jinaScoringModel(final JinaScoringProperties properties,
			final ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			final ObjectProvider<RetryTemplate> retryTemplate,
			final ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		Assert.hasText(properties.getApiKey(), "Jina API key must be set");
		Assert.hasText(properties.getBaseUrl(), "Jina base URL must be set");

		var jinaScoringApi = JinaScoringApi.builder()
			.baseUrl(properties.getBaseUrl())
			.apiKey(properties.getApiKey())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();

		return JinaScoringModel.builder()
			.jinaScoringApi(jinaScoringApi)
			.options(properties.getOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
	}

}
