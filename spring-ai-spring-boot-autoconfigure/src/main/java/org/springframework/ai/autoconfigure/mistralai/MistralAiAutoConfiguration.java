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
package org.springframework.ai.autoconfigure.mistralai;

import java.util.List;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.mistralai.MistralAiChatClient;
import org.springframework.ai.mistralai.MistralAiEmbeddingClient;
import org.springframework.ai.mistralai.api.MistralAiApi;
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
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @since 0.8.1
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiEmbeddingProperties.class, MistralAiCommonProperties.class,
		MistralAiChatProperties.class })
@ConditionalOnClass(MistralAiApi.class)
public class MistralAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public MistralAiEmbeddingClient mistralAiEmbeddingClient(MistralAiCommonProperties commonProperties,
			MistralAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var mistralAiApi = mistralAiApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(), restClientBuilder,
				responseErrorHandler);

		return new MistralAiEmbeddingClient(mistralAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public MistralAiChatClient mistralAiChatClient(MistralAiCommonProperties commonProperties,
			MistralAiChatProperties chatProperties, RestClient.Builder restClientBuilder,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackContext functionCallbackContext,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		var mistralAiApi = mistralAiApi(chatProperties.getApiKey(), commonProperties.getApiKey(),
				chatProperties.getBaseUrl(), commonProperties.getBaseUrl(), restClientBuilder, responseErrorHandler);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new MistralAiChatClient(mistralAiApi, chatProperties.getOptions(), functionCallbackContext,
				retryTemplate);
	}

	private MistralAiApi mistralAiApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resoledBaseUrl, "Mistral base URL must be set");

		return new MistralAiApi(resoledBaseUrl, resolvedApiKey, restClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
