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

import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechConnectionDetails;
import org.springframework.ai.google.genai.tts.GoogleGenAiTextToSpeechModel;
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

/**
 * Auto-configuration for the Google GenAI Gemini-TTS text-to-speech model.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
@AutoConfiguration(after = GoogleGenAiTextToSpeechConnectionAutoConfiguration.class)
@ConditionalOnClass(GoogleGenAiTextToSpeechModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_SPEECH_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = true)
@EnableConfigurationProperties(GoogleGenAiTextToSpeechProperties.class)
public class GoogleGenAiTextToSpeechAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoogleGenAiTextToSpeechModel googleGenAiTextToSpeechModel(
			GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiTextToSpeechProperties speechProperties, ObjectProvider<RetryTemplate> retryTemplate) {

		return GoogleGenAiTextToSpeechModel.builder()
			.connectionDetails(connectionDetails)
			.options(speechProperties.toOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
	}

}
