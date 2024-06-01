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
package org.springframework.ai.autoconfigure.dashscope;

import java.util.List;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.dashscope.DashscopeChatModel;
import org.springframework.ai.dashscope.DashscopeEmbeddingModel;
import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * @author Nottyjay
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(DashscopeApi.class)
@EnableConfigurationProperties({ DashscopeConnectionProperties.class, DashscopeChatProperties.class,
		DashscopeEmbeddingProperties.class })
public class DashscopeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashscopeChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashscopeChatModel dashscopeChatClient(DashscopeConnectionProperties commonProperties,
			DashscopeChatProperties chatProperties, RestClient.Builder restClientBuilder,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackContext functionCallbackContext,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var dashscopeApi = dashscopeApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties.getApiKey(), restClientBuilder, responseErrorHandler);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new DashscopeChatModel(dashscopeApi, chatProperties.getOptions(), functionCallbackContext,
				retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashscopeEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashscopeEmbeddingModel dashscopeEmbeddingClient(DashscopeConnectionProperties commonProperties,
			DashscopeEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var dashscopeApi = dashscopeApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties.getApiKey(), restClientBuilder, responseErrorHandler);

		return new DashscopeEmbeddingModel(dashscopeApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	private DashscopeApi dashscopeApi(String baseUrl, String commonBaseUrl, String apiKey, String commonApiKey,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl, "Dashscope base URL must be set");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey, "Dashscope API key must be set");

		return new DashscopeApi(resolvedBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
