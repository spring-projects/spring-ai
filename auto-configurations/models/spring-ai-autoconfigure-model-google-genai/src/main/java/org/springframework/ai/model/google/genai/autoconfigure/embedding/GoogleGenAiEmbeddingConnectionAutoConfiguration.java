/*
 * Copyright 2023-2025 the original author or authors.
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

import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
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
@EnableConfigurationProperties(GoogleGenAiEmbeddingConnectionProperties.class)
public class GoogleGenAiEmbeddingConnectionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
			GoogleGenAiEmbeddingConnectionProperties connectionProperties) throws IOException {

		var connectionBuilder = GoogleGenAiEmbeddingConnectionDetails.builder();

		if (StringUtils.hasText(connectionProperties.getApiKey())) {
			// Gemini Developer API mode
			connectionBuilder.apiKey(connectionProperties.getApiKey());
		}
		else {
			// Vertex AI mode
			Assert.hasText(connectionProperties.getProjectId(), "Google GenAI project-id must be set!");
			Assert.hasText(connectionProperties.getLocation(), "Google GenAI location must be set!");

			connectionBuilder.projectId(connectionProperties.getProjectId())
				.location(connectionProperties.getLocation());

			if (connectionProperties.getCredentialsUri() != null) {
				GoogleCredentials credentials = GoogleCredentials
					.fromStream(connectionProperties.getCredentialsUri().getInputStream());
				// Note: Credentials are handled automatically by the SDK when using
				// Vertex AI mode
			}
		}

		return connectionBuilder.build();
	}

}
