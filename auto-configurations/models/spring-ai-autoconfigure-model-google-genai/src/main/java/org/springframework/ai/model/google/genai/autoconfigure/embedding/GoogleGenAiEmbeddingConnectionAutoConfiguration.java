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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Google GenAI Embedding Connection.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ Client.class, GoogleGenAiEmbeddingConnectionDetails.class })
@EnableConfigurationProperties({ GoogleGenAiEmbeddingConnectionProperties.class,
		GoogleGenAiConnectionProperties.class })
public class GoogleGenAiEmbeddingConnectionAutoConfiguration {

	private static final Log logger = LogFactory.getLog(GoogleGenAiEmbeddingConnectionAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
			GoogleGenAiEmbeddingConnectionProperties connectionProperties,
			GoogleGenAiConnectionProperties sharedConnectionProperties) throws IOException {

		var connectionBuilder = GoogleGenAiEmbeddingConnectionDetails.builder();

		// The embedding-specific properties win; the shared spring.ai.google.genai.*
		// properties act as fallbacks so a single api-key (or Vertex AI
		// project/location) can configure the chat and embedding modules together.
		var apiKey = StringUtils.hasText(connectionProperties.getApiKey()) ? connectionProperties.getApiKey()
				: sharedConnectionProperties.getApiKey();
		var projectId = StringUtils.hasText(connectionProperties.getProjectId()) ? connectionProperties.getProjectId()
				: sharedConnectionProperties.getProjectId();
		var location = StringUtils.hasText(connectionProperties.getLocation()) ? connectionProperties.getLocation()
				: sharedConnectionProperties.getLocation();
		var credentialsUri = connectionProperties.getCredentialsUri() != null ? connectionProperties.getCredentialsUri()
				: sharedConnectionProperties.getCredentialsUri();

		boolean hasApiKey = StringUtils.hasText(apiKey);
		boolean vertexAi = connectionProperties.isVertexAi() || sharedConnectionProperties.isVertexAi();

		// Ambiguity Guard: Professional logging
		if (hasApiKey && StringUtils.hasText(projectId) && StringUtils.hasText(location)) {
			if (vertexAi) {
				logger.info(
						"Both API Key and Vertex AI config detected. Vertex AI mode is explicitly enabled; the API key will be ignored.");
			}
			else {
				logger.warn("Both API Key and Vertex AI config detected. Defaulting to Gemini Developer API (API Key). "
						+ "To use Vertex AI instead, set 'spring.ai.google.genai.vertex-ai=true'.");
			}
		}

		// Mode Selection with Fail-Fast Validation
		if (vertexAi) {
			if (projectId == null || location == null) {
				throw new IllegalStateException(
						"Vertex AI mode requires both 'project-id' and 'location' to be configured.");
			}
			configureVertexAi(connectionBuilder, projectId, location, credentialsUri);
		}
		else if (hasApiKey) {
			connectionBuilder.apiKey(apiKey);
		}
		else if (projectId != null && location != null) {
			logger.debug("Project ID and Location detected. Defaulting to Vertex AI mode.");
			configureVertexAi(connectionBuilder, projectId, location, credentialsUri);
		}
		else {
			throw new IllegalStateException(
					"Incomplete Google GenAI configuration: Provide 'spring.ai.google.genai.embedding.api-key' (or the shared 'spring.ai.google.genai.api-key') for the Gemini Developer API, "
							+ "or 'project-id' and 'location' for Vertex AI.");
		}

		return connectionBuilder.build();
	}

	private void configureVertexAi(GoogleGenAiEmbeddingConnectionDetails.Builder connectionBuilder,
			@Nullable String projectId, @Nullable String location, @Nullable Resource credentialsUri)
			throws IOException {

		connectionBuilder.projectId(projectId).location(location);

		if (credentialsUri != null) {
			try (var is = credentialsUri.getInputStream()) {
				connectionBuilder.credentials(GoogleCredentials.fromStream(is));
			}
		}
	}

}
