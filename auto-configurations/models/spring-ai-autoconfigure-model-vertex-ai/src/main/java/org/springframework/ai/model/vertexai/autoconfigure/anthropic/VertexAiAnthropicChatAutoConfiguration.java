/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.anthropic;

import java.io.IOException;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.api.PredictionServiceSettings;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AutoConfiguration for Anthropic chat models hosted on Google Cloud Vertex AI.
 * <p>
 * Requires the {@code spring.ai.vertex.ai.anthropic} configuration prefix for
 * customization.
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		ToolCallingAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@EnableConfigurationProperties({ VertexAiAnthropicChatProperties.class, VertexAiAnthropicConnectionProperties.class })
@ConditionalOnClass(VertexAiAnthropicApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.ANTHROPIC,
		matchIfMissing = true)
@Import(StringToToolChoiceConverter.class)
public class VertexAiAnthropicChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiAnthropicApi vertexAiAnthropicApi(VertexAiAnthropicConnectionProperties connectionProperties,
			VertexAiAnthropicChatProperties chatProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ResponseErrorHandler responseErrorHandler)
			throws IOException {

		return VertexAiAnthropicApi.builderForVertexAi()
			.projectId(connectionProperties.getProjectId())
			.credentials(vertexAiCredential(connectionProperties))
			.location(connectionProperties.getLocation())
			.model(chatProperties.getOptions().getModel())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

	public Credentials vertexAiCredential(VertexAiAnthropicConnectionProperties connectionProperties)
			throws IOException {
		if (connectionProperties.getCredentialsUri() != null) {
			return GoogleCredentials.fromStream(connectionProperties.getCredentialsUri().getInputStream());
		}
		return PredictionServiceSettings.defaultCredentialsProviderBuilder().build().getCredentials();

	}

	@Bean
	@ConditionalOnMissingBean
	public AnthropicChatModel vertexAiAnthropicChatModel(VertexAiAnthropicApi anthropicApi,
			VertexAiAnthropicConnectionProperties connectionProperties, VertexAiAnthropicChatProperties chatProperties,
			RetryTemplate retryTemplate, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> anthropicToolExecutionEligibilityPredicate) {
		var chatModel = AnthropicChatModel.builder()
			.anthropicApi(anthropicApi)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(anthropicToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.retryTemplate(retryTemplate)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

}
