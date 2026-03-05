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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.RetryUtils;
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
 * @author Yanming Zhou
 * @since 1.1.0
 */
@AutoConfiguration(after = { SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ Client.class, GoogleGenAiChatModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = true)
@EnableConfigurationProperties({ GoogleGenAiChatProperties.class, GoogleGenAiConnectionProperties.class })
public class GoogleGenAiChatAutoConfiguration {

	private static final Log logger = LogFactory.getLog(GoogleGenAiChatAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public Client googleGenAiClient(GoogleGenAiConnectionProperties properties) throws IOException {
		Client.Builder builder = Client.builder();

		boolean hasApiKey = StringUtils.hasText(properties.getApiKey());
		boolean hasProject = StringUtils.hasText(properties.getProjectId());
		boolean hasLocation = StringUtils.hasText(properties.getLocation());
		boolean hasVertexConfig = hasProject && hasLocation;

		// Ambiguity Guard: Professional logging
		if (hasApiKey && hasVertexConfig) {
			if (properties.isVertexAi()) {
				logger.info(
						"Both API Key and Vertex AI config detected. Vertex AI mode is explicitly enabled; the API key will be ignored.");
			}
			else {
				logger.warn("Both API Key and Vertex AI config detected. Defaulting to Gemini Developer API (API Key). "
						+ "To use Vertex AI instead, set 'spring.ai.google.genai.vertex-ai=true'.");
			}
		}

		// Mode Selection with Fail-Fast Validation
		if (properties.isVertexAi()) {
			if (!hasVertexConfig) {
				throw new IllegalStateException(
						"Vertex AI mode requires both 'project-id' and 'location' to be configured.");
			}
			configureVertexAi(builder, properties);
		}
		else if (hasApiKey) {
			builder.apiKey(properties.getApiKey());
		}
		else if (hasVertexConfig) {
			logger.debug("Project ID and Location detected. Defaulting to Vertex AI mode.");
			configureVertexAi(builder, properties);
		}
		else {
			throw new IllegalStateException("Incomplete Google GenAI configuration: Provide 'api-key' for Gemini API "
					+ "or 'project-id' and 'location' for Vertex AI.");
		}

		return builder.build();
	}

	private boolean isVertexAiConfiguration(GoogleGenAiConnectionProperties props) {
		return props.isVertexAi()
				|| (StringUtils.hasText(props.getProjectId()) && StringUtils.hasText(props.getLocation()));
	}

	private void configureVertexAi(Client.Builder builder, GoogleGenAiConnectionProperties props) throws IOException {
		Assert.hasText(props.getProjectId(), "Google GenAI project-id must be set for Vertex AI mode!");
		Assert.hasText(props.getLocation(), "Google GenAI location must be set for Vertex AI mode!");

		builder.project(props.getProjectId()).location(props.getLocation()).vertexAI(true);

		if (props.getCredentialsUri() != null) {
			try (var is = props.getCredentialsUri().getInputStream()) {
				builder.credentials(GoogleCredentials.fromStream(is));
			}
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiChatModel googleGenAiChatModel(Client googleGenAiClient, GoogleGenAiChatProperties chatProperties,
			ToolCallingManager toolCallingManager, ApplicationContext context,
			ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {

		GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(googleGenAiClient)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(
					toolExecutionEligibilityPredicate.getIfUnique(() -> new DefaultToolExecutionEligibilityPredicate()))
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
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
