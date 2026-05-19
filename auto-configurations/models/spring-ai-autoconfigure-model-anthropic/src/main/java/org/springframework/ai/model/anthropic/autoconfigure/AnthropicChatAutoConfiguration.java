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

package org.springframework.ai.model.anthropic.autoconfigure;

import com.anthropic.client.AnthropicClient;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Anthropic Chat Model.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ AnthropicConnectionProperties.class, AnthropicChatProperties.class })
@ConditionalOnClass(AnthropicClient.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.ANTHROPIC,
		matchIfMissing = true)
public class AnthropicChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AnthropicChatModel anthropicChatModel(AnthropicConnectionProperties connectionProperties,
			AnthropicChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> anthropicToolExecutionEligibilityPredicate) {

		AnthropicChatOptions.Builder builder = chatProperties.toOptions().mutate();
		if (connectionProperties.getApiKey() != null) {
			builder.apiKey(connectionProperties.getApiKey());
		}
		if (connectionProperties.getBaseUrl() != null) {
			builder.baseUrl(connectionProperties.getBaseUrl());
		}
		if (connectionProperties.getTimeout() != null) {
			builder.timeout(connectionProperties.getTimeout());
		}
		if (connectionProperties.getMaxRetries() != null) {
			builder.maxRetries(connectionProperties.getMaxRetries());
		}
		if (connectionProperties.getProxy() != null) {
			builder.proxy(connectionProperties.getProxy());
		}
		if (!connectionProperties.getCustomHeaders().isEmpty()) {
			builder.customHeaders(connectionProperties.getCustomHeaders());
		}
		AnthropicChatOptions options = builder.build();

		var chatModel = AnthropicChatModel.builder()
			.options(options)
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolExecutionEligibilityPredicate(anthropicToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
