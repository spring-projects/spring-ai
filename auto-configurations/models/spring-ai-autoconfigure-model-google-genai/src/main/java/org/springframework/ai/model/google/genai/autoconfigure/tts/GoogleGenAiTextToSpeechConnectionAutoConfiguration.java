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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;

import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for the Google GenAI Gemini-TTS connection.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
@AutoConfiguration
@ConditionalOnClass({ Client.class, GoogleGenAiTextToSpeechConnectionDetails.class })
@EnableConfigurationProperties(GoogleGenAiTextToSpeechConnectionProperties.class)
public class GoogleGenAiTextToSpeechConnectionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiTextToSpeechConnectionDetails googleGenAiTextToSpeechConnectionDetails(
			GoogleGenAiTextToSpeechConnectionProperties connectionProperties) throws IOException {

		var connectionBuilder = GoogleGenAiTextToSpeechConnectionDetails.builder()
			.apiKey(connectionProperties.getApiKey())
			.projectId(connectionProperties.getProjectId())
			.location(connectionProperties.getLocation());

		if (!StringUtils.hasText(connectionProperties.getApiKey())
				&& connectionProperties.getCredentialsUri() != null) {
			try (var is = connectionProperties.getCredentialsUri().getInputStream()) {
				connectionBuilder.credentials(GoogleCredentials.fromStream(is));
			}
		}

		return connectionBuilder.build();
	}

}
