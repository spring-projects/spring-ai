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

import java.util.List;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.autoconfigure.common.function.SpringAiFunctionAnnotationManager;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.model.ToolFunctionCallback;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = { RestClientAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class,
		OpenAiEmbeddingProperties.class, OpenAiImageProperties.class })
@ImportRuntimeHints(NativeHints.class)
/**
 * @author Christian Tzolov
 */
public class OpenAiAutoConfiguration {

	public static final String OPEN_AI_API_KEY_MUST_BE_SET = "OpenAI API key must be set";

	public static final String OPEN_AI_BASE_URL_MUST_BE_SET = "OpenAI base URL must be set";

	@Bean
	@ConditionalOnMissingBean
	public OpenAiChatClient openAiChatClient(OpenAiConnectionProperties commonProperties,
			OpenAiChatProperties chatProperties, RestClient.Builder restClientBuilder,
			List<ToolFunctionCallback> toolFunctionCallbacks, SpringAiFunctionAnnotationManager functionManager) {

		String apiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, OPEN_AI_API_KEY_MUST_BE_SET);
		Assert.hasText(baseUrl, OPEN_AI_BASE_URL_MUST_BE_SET);

		var openAiApi = new OpenAiApi(baseUrl, apiKey, restClientBuilder);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getToolCallbacks().addAll(toolFunctionCallbacks);
		}

		var annotatedFunctionsList = functionManager.getAnnotatedToolFunctionCallbacks();
		if (!CollectionUtils.isEmpty(annotatedFunctionsList)) {
			chatProperties.getOptions().getToolCallbacks().addAll(annotatedFunctionsList);
		}

		return new OpenAiChatClient(openAiApi, chatProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public EmbeddingClient openAiEmbeddingClient(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder) {

		String apiKey = StringUtils.hasText(embeddingProperties.getApiKey()) ? embeddingProperties.getApiKey()
				: commonProperties.getApiKey();
		String baseUrl = StringUtils.hasText(embeddingProperties.getBaseUrl()) ? embeddingProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, OPEN_AI_API_KEY_MUST_BE_SET);
		Assert.hasText(baseUrl, OPEN_AI_BASE_URL_MUST_BE_SET);

		var openAiApi = new OpenAiApi(baseUrl, apiKey, restClientBuilder);

		return new OpenAiEmbeddingClient(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiImageClient openAiImageClient(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, RestClient.Builder restClientBuilder) {
		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, OPEN_AI_API_KEY_MUST_BE_SET);
		Assert.hasText(baseUrl, OPEN_AI_BASE_URL_MUST_BE_SET);

		var openAiImageApi = new OpenAiImageApi(baseUrl, apiKey, restClientBuilder);

		return new OpenAiImageClient(openAiImageApi).withDefaultOptions(imageProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public SpringAiFunctionAnnotationManager springAiFunctionManager(ApplicationContext context) {
		SpringAiFunctionAnnotationManager manager = new SpringAiFunctionAnnotationManager();
		manager.setApplicationContext(context);
		return manager;
	}

}
