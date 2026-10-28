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

package org.springframework.ai.model.deliverance.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.deliverance.DeliveranceChatModel;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.ToolCallingManager;
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
 * {@link AutoConfiguration Auto-configuration} for Deliverance Chat Model.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
@AutoConfiguration
@ConditionalOnClass(DeliveranceApi.class)
@EnableConfigurationProperties({ DeliveranceConnectionProperties.class, DeliveranceChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.DELIVERANCE)
public class DeliveranceChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public DeliveranceApi deliveranceApi(DeliveranceConnectionProperties connectionProperties,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
		return DeliveranceApi.builder()
			.baseUrl(connectionProperties.getBaseUrl())
			.username(connectionProperties.getUsername())
			.password(connectionProperties.getPassword())
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public DeliveranceChatModel deliveranceChatModel(DeliveranceApi deliveranceApi,
			DeliveranceChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {
		DeliveranceChatModel chatModel = DeliveranceChatModel.builder()
			.deliveranceApi(deliveranceApi)
			.objectMapper(new ObjectMapper())
			.options(chatProperties.toOptions())
			.toolCallingManager(toolCallingManager)
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

}
