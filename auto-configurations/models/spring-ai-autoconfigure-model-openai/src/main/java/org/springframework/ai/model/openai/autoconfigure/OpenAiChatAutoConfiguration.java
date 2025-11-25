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

package org.springframework.ai.model.openai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
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
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

/**
 * Chat {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Christian Tzolov
 * @author Stefan Vassilev
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
public class OpenAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiApi openAiApi(OpenAiConnectionProperties commonProperties, OpenAiChatProperties chatProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ResponseErrorHandler responseErrorHandler) {

		OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
				commonProperties, chatProperties, "chat");

		return OpenAiApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.completionsPath(chatProperties.getCompletionsPath())
			.embeddingsPath(OpenAiEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH)
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatProperties chatProperties,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> openAiToolExecutionEligibilityPredicate) {

		var chatModel = OpenAiChatModel.builder()
			.openAiApi(openAiApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(
					openAiToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
