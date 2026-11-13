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

package org.springframework.ai.model.reckoning.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.reckoning.ReckoningChatModel;
import org.springframework.ai.reckoning.api.ReckoningApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the Reckoning chat model.
 *
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass(ReckoningChatModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.RECKONING,
		matchIfMissing = true)
@EnableConfigurationProperties({ ReckoningChatProperties.class, ReckoningConnectionProperties.class })
public class ReckoningChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ReckoningApi reckoningApi(ReckoningConnectionProperties properties,
			ObjectProvider<WebClient.Builder> webClientBuilder) {
		return ReckoningApi.builder()
			.baseUrl(properties.getBaseUrl())
			.username(properties.getUsername())
			.password(properties.getPassword())
			.webClientBuilder(webClientBuilder.getIfAvailable(WebClient::builder))
			.build();
	}

	@Bean
	@ConditionalOnMissingBean(ChatModel.class)
	public ReckoningChatModel reckoningChatModel(ReckoningApi reckoningApi, ReckoningChatProperties properties,
			ObjectProvider<org.springframework.ai.model.tool.ToolCallingManager> toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<RetryTemplate> retryTemplate) {
		ReckoningChatModel chatModel = ReckoningChatModel.builder()
			.reckoningApi(reckoningApi)
			.objectMapper(new ObjectMapper())
			.options(properties.toOptions())
			.toolCallingManager(toolCallingManager
				.getIfAvailable(() -> org.springframework.ai.model.tool.ToolCallingManager.builder().build()))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

}
