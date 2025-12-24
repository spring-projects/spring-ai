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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Google GenAI Chat.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.1.0
 */
@AutoConfiguration(after = { SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ Client.class, GoogleGenAiChatModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = true)
@EnableConfigurationProperties({ GoogleGenAiChatProperties.class, GoogleGenAiConnectionProperties.class })
public class GoogleGenAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Client googleGenAiClient(GoogleGenAiConnectionProperties connectionProperties) throws IOException {

		Client.Builder clientBuilder = Client.builder();

		if (StringUtils.hasText(connectionProperties.getApiKey())) {
			// Gemini Developer API mode
			clientBuilder.apiKey(connectionProperties.getApiKey());
		}
		else {
			// Vertex AI mode
			Assert.hasText(connectionProperties.getProjectId(), "Google GenAI project-id must be set!");
			Assert.hasText(connectionProperties.getLocation(), "Google GenAI location must be set!");

			clientBuilder.project(connectionProperties.getProjectId())
				.location(connectionProperties.getLocation())
				.vertexAI(true);

			if (connectionProperties.getCredentialsUri() != null) {
				GoogleCredentials credentials = GoogleCredentials
					.fromStream(connectionProperties.getCredentialsUri().getInputStream());
				// Note: The new SDK doesn't have a direct setCredentials method,
				// credentials are handled automatically when vertexAI is true
			}
		}

		return clientBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiChatModel googleGenAiChatModel(Client googleGenAiClient, GoogleGenAiChatProperties chatProperties,
			ToolCallingManager toolCallingManager, ApplicationContext context, RetryTemplate retryTemplate,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {

		GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(googleGenAiClient)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(
					toolExecutionEligibilityPredicate.getIfUnique(() -> new DefaultToolExecutionEligibilityPredicate()))
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnBean(GoogleGenAiChatModel.class)
	@ConditionalOnMissingBean
	@Conditional(CachedContentServiceCondition.class)
	@ConditionalOnProperty(prefix = "spring.ai.google.genai.chat", name = "enable-cached-content", havingValue = "true",
			matchIfMissing = true)
	public GoogleGenAiCachedContentService googleGenAiCachedContentService(GoogleGenAiChatModel chatModel) {
		// Extract the cached content service from the chat model
		// The CachedContentServiceCondition ensures this is not null
		return chatModel.getCachedContentService();
	}

}
