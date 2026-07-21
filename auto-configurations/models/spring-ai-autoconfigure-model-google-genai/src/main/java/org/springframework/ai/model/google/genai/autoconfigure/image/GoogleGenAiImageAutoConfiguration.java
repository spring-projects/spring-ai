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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiImageModel;
import org.springframework.ai.google.genai.api.GoogleGenAiImageApi;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.google.genai.autoconfigure.chat.CachedContentServiceCondition;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
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

import java.io.IOException;

/**
 * Auto-configuration for Google GenAI Image.
 *
 * @author Danil Temnikov
 */
@AutoConfiguration(after = { SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ Client.class, GoogleGenAiImageModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = true)
@EnableConfigurationProperties({ GoogleGenAiImageProperties.class, GoogleGenAiImageConnectionProperties.class })
public class GoogleGenAiImageAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiImageAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public Client googleGenAiImageClient(GoogleGenAiConnectionProperties connectionProperties) throws IOException {

		Client.Builder clientBuilder = Client.builder();
		logger.error("Connection properties: {}", connectionProperties);

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
	public GoogleGenAiImageModel googleGenAiImageModel(GoogleGenAiImageApi googleGenAiImageApi,
			GoogleGenAiImageProperties properties) {
		return new GoogleGenAiImageModel(googleGenAiImageApi, properties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiImageApi googleGenAiImageApi(Client googleGenAiClient) {
		return new GoogleGenAiImageApi(googleGenAiClient);
	}

}
