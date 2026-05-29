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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.google.genai.image.GoogleGenAiImageConnectionDetails;
import org.springframework.ai.google.genai.image.GoogleGenAiImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Google GenAI Image.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ Client.class, GoogleGenAiImageModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = true)
@EnableConfigurationProperties({ GoogleGenAiImageConnectionProperties.class, GoogleGenAiImageProperties.class })
public class GoogleGenAiImageAutoConfiguration {

	private static final Log logger = LogFactory.getLog(GoogleGenAiImageAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiImageConnectionDetails googleGenAiImageConnectionDetails(
			GoogleGenAiImageConnectionProperties properties) throws IOException {

		boolean hasApiKey = StringUtils.hasText(properties.getApiKey());
		boolean hasProject = StringUtils.hasText(properties.getProjectId());
		boolean hasLocation = StringUtils.hasText(properties.getLocation());
		boolean hasVertexConfig = hasProject && hasLocation;

		if (hasApiKey && hasVertexConfig) {
			if (properties.isVertexAi()) {
				logger.info(
						"Both API Key and Vertex AI config detected. Vertex AI mode is explicitly enabled; the API key will be ignored.");
			}
			else {
				logger.warn("Both API Key and Vertex AI config detected. Defaulting to Gemini Developer API (API Key). "
						+ "To use Vertex AI instead, set 'spring.ai.google.genai.image.vertex-ai=true'.");
			}
		}

		var connectionBuilder = GoogleGenAiImageConnectionDetails.builder();

		if (properties.isVertexAi()) {
			Assert.isTrue(hasVertexConfig,
					"Vertex AI mode requires both 'project-id' and 'location' to be configured.");
			connectionBuilder.projectId(properties.getProjectId()).location(properties.getLocation());
			loadCredentialsIfNeeded(properties);
		}
		else if (hasApiKey) {
			connectionBuilder.apiKey(properties.getApiKey());
		}
		else if (hasVertexConfig) {
			logger.debug("Project ID and Location detected. Defaulting to Vertex AI mode.");
			connectionBuilder.projectId(properties.getProjectId()).location(properties.getLocation());
			loadCredentialsIfNeeded(properties);
		}
		else {
			throw new IllegalStateException(
					"Incomplete Google GenAI Image configuration: Provide 'api-key' for Gemini API "
							+ "or 'project-id' and 'location' for Vertex AI.");
		}

		return connectionBuilder.build();
	}

	private void loadCredentialsIfNeeded(GoogleGenAiImageConnectionProperties properties) throws IOException {
		if (properties.getCredentialsUri() != null) {
			// Credentials are automatically discovered by the GenAI SDK when using
			// Vertex AI mode (Application Default Credentials). Reading the resource
			// here simply verifies it exists and surfaces I/O errors early.
			try (var is = properties.getCredentialsUri().getInputStream()) {
				GoogleCredentials.fromStream(is);
			}
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiImageModel googleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageProperties imageProperties, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ImageModelObservationConvention> observationConvention) {

		GoogleGenAiImageModel imageModel = new GoogleGenAiImageModel(connectionDetails, imageProperties.getOptions(),
				retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(imageModel::setObservationConvention);

		return imageModel;
	}

}
