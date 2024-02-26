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

package org.springframework.ai.autoconfigure.openai;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = { RestClientAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class,
		OpenAiEmbeddingProperties.class, OpenAiImageProperties.class })
/**
 * @author Christian Tzolov
 */
public class OpenAiAutoConfiguration {

	public static final String OPEN_AI_API_KEY_MUST_BE_SET = "OpenAI API key must be set";

	public static final String OPEN_AI_BASE_URL_MUST_BE_SET = "OpenAI base URL must be set";

	public static final String OPEN_AI_READ_TIMEOUT_MUST_BE_SET = "OpenAI base read timeout must be set";

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiChatClient openAiChatClient(OpenAiConnectionProperties commonProperties,
			OpenAiChatProperties chatProperties, RestClient.Builder restClientBuilder,
			List<FunctionCallback> toolFunctionCallbacks, FunctionCallbackContext functionCallbackContext) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				chatProperties);
		RestClient.Builder overridenRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		var openAiApi = new OpenAiApi(overridenCommonProperties.getBaseUrl(), overridenCommonProperties.getApiKey(),
				overridenRestClientBuilder);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new OpenAiChatClient(openAiApi, chatProperties.getOptions(), functionCallbackContext);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				embeddingProperties);
		RestClient.Builder overridenRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		var openAiApi = new OpenAiApi(overridenCommonProperties.getBaseUrl(), overridenCommonProperties.getApiKey(),
				overridenRestClientBuilder);

		return new OpenAiEmbeddingClient(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiImageClient openAiImageClient(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, RestClient.Builder restClientBuilder) {

		OpenAiConnectionProperties overridenCommonProperties = checkAndOverrideProperties(commonProperties,
				imageProperties);
		RestClient.Builder overridenRestClientBuilder = overrideRestClientBuilder(restClientBuilder,
				overridenCommonProperties);

		var openAiImageApi = new OpenAiImageApi(overridenCommonProperties.getBaseUrl(),
				overridenCommonProperties.getApiKey(), overridenRestClientBuilder);

		return new OpenAiImageClient(openAiImageApi).withDefaultOptions(imageProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

	private static <T extends OpenAiParentProperties> OpenAiConnectionProperties checkAndOverrideProperties(
			OpenAiConnectionProperties commonProperties, T specificProperties) {

		String apiKey = StringUtils.hasText(specificProperties.getApiKey()) ? specificProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(specificProperties.getBaseUrl()) ? specificProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Duration readTimeout = specificProperties.getReadTimeout() != null ? specificProperties.getReadTimeout()
				: commonProperties.getReadTimeout();

		Assert.hasText(apiKey, OPEN_AI_API_KEY_MUST_BE_SET);
		Assert.hasText(baseUrl, OPEN_AI_BASE_URL_MUST_BE_SET);
		Assert.notNull(readTimeout, OPEN_AI_READ_TIMEOUT_MUST_BE_SET);

		OpenAiConnectionProperties overridenCommonProperties = new OpenAiConnectionProperties();
		overridenCommonProperties.setApiKey(apiKey);
		overridenCommonProperties.setBaseUrl(baseUrl);
		overridenCommonProperties.setReadTimeout(readTimeout);

		return overridenCommonProperties;

	}

	private static RestClient.Builder overrideRestClientBuilder(RestClient.Builder restClientBuilder,
			OpenAiConnectionProperties overridenCommonProperties) {
		ClientHttpRequestFactorySettings requestFactorySettings = new ClientHttpRequestFactorySettings(
				Duration.ofHours(1l), overridenCommonProperties.getReadTimeout(), SslBundle.of(null));
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(requestFactorySettings);
		return restClientBuilder.clone().requestFactory(requestFactory);
	}

}
