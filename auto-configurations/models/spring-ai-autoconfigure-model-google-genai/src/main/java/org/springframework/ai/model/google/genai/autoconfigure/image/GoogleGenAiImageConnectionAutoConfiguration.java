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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.google.genai.image.GoogleGenAiImageConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Google GenAI Image Connection.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@AutoConfiguration
@ConditionalOnClass({ Client.class, GoogleGenAiImageConnectionDetails.class })
@EnableConfigurationProperties(GoogleGenAiImageConnectionProperties.class)
public class GoogleGenAiImageConnectionAutoConfiguration {

	private static final Log logger = LogFactory.getLog(GoogleGenAiImageConnectionAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiImageConnectionDetails googleGenAiImageConnectionDetails(
			GoogleGenAiImageConnectionProperties connectionProperties) throws IOException {

		var connectionBuilder = GoogleGenAiImageConnectionDetails.builder();

		boolean hasApiKey = StringUtils.hasText(connectionProperties.getApiKey());
		boolean hasProject = StringUtils.hasText(connectionProperties.getProjectId());
		boolean hasLocation = StringUtils.hasText(connectionProperties.getLocation());
		boolean hasVertexConfig = hasProject && hasLocation;

		// Ambiguity Guard: Professional logging
		if (hasApiKey && hasVertexConfig) {
			if (connectionProperties.isVertexAi()) {
				logger.info(
						"Both API Key and Vertex AI config detected. Vertex AI mode is explicitly enabled; the API key will be ignored.");
			}
			else {
				logger.warn("Both API Key and Vertex AI config detected. Defaulting to Gemini Developer API (API Key). "
						+ "To use Vertex AI instead, set 'spring.ai.google.genai.vertex-ai=true'.");
			}
		}

		// Mode Selection with Fail-Fast Validation
		if (connectionProperties.isVertexAi()) {
			if (!hasVertexConfig) {
				throw new IllegalStateException(
						"Vertex AI mode requires both 'project-id' and 'location' to be configured.");
			}
			configureVertexAi(connectionBuilder, connectionProperties);
		}
		else if (hasApiKey) {
			connectionBuilder.apiKey(connectionProperties.getApiKey());
		}
		else if (hasVertexConfig) {
			logger.debug("Project ID and Location detected. Defaulting to Vertex AI mode.");
			configureVertexAi(connectionBuilder, connectionProperties);
		}
		else {
			throw new IllegalStateException("Incomplete Google GenAI configuration: Provide 'api-key' for Gemini API "
					+ "or 'project-id' and 'location' for Vertex AI.");
		}

		return connectionBuilder.build();
	}

	private void configureVertexAi(GoogleGenAiImageConnectionDetails.Builder connectionBuilder,
			GoogleGenAiImageConnectionProperties connectionProperties) throws IOException {
		Assert.hasText(connectionProperties.getProjectId(), "Google GenAI project-id must be set for Vertex AI mode!");
		Assert.hasText(connectionProperties.getLocation(), "Google GenAI location must be set for Vertex AI mode!");

		connectionBuilder.projectId(connectionProperties.getProjectId()).location(connectionProperties.getLocation());

		if (connectionProperties.getCredentialsUri() != null) {
			try (var is = connectionProperties.getCredentialsUri().getInputStream()) {
				connectionBuilder.credentials(GoogleCredentials.fromStream(is));
			}
		}
	}

}
