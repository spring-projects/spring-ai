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

package org.springframework.ai.model.google.genai.autoconfigure.transcription;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechClient;

import org.springframework.ai.google.genai.transcription.GoogleGenAiTranscriptionConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

/**
 * Auto-configuration for the Google GenAI Transcription connection.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@AutoConfiguration
@ConditionalOnClass({ SpeechClient.class, GoogleGenAiTranscriptionConnectionDetails.class })
@EnableConfigurationProperties(GoogleGenAiTranscriptionConnectionProperties.class)
public class GoogleGenAiTranscriptionConnectionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiTranscriptionConnectionDetails googleGenAiTranscriptionConnectionDetails(
			GoogleGenAiTranscriptionConnectionProperties connectionProperties) throws IOException {

		Assert.hasText(connectionProperties.getProjectId(),
				"Google GenAI transcription project-id must be set. Speech-to-Text V2 resource names require a project.");

		var connectionBuilder = GoogleGenAiTranscriptionConnectionDetails.builder()
			.projectId(connectionProperties.getProjectId())
			.location(connectionProperties.getLocation());

		if (connectionProperties.getCredentialsUri() != null) {
			try (var is = connectionProperties.getCredentialsUri().getInputStream()) {
				connectionBuilder.credentials(GoogleCredentials.fromStream(is));
			}
		}

		return connectionBuilder.build();
	}

}
