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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.springframework.ai.google.genai.tts.GeminiTtsModel;
import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
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
 * Auto-configuration for Google GenAI Text-to-Speech.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@AutoConfiguration(after = SpringAiRetryAutoConfiguration.class)
@ConditionalOnClass({ GeminiTtsApi.class, GeminiTtsModel.class })
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_SPEECH_MODEL, havingValue = SpringAIModels.GOOGLE_GEN_AI,
		matchIfMissing = false)
@EnableConfigurationProperties({ GoogleGenAiTtsProperties.class, GoogleGenAiTtsConnectionProperties.class })
public class GoogleGenAiTtsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GeminiTtsApi geminiTtsApi(GoogleGenAiTtsConnectionProperties connectionProperties) {
		Assert.hasText(connectionProperties.getApiKey(), "Google GenAI API key must be set!");

		if (StringUtils.hasText(connectionProperties.getBaseUrl())) {
			return new GeminiTtsApi(connectionProperties.getApiKey(), connectionProperties.getBaseUrl());
		}

		return new GeminiTtsApi(connectionProperties.getApiKey());
	}

	@Bean
	@ConditionalOnMissingBean
	public GeminiTtsModel geminiTtsModel(GeminiTtsApi geminiTtsApi, GoogleGenAiTtsProperties ttsProperties,
			RetryTemplate retryTemplate) {

		return GeminiTtsModel.builder()
			.geminiTtsApi(geminiTtsApi)
			.defaultOptions(ttsProperties.getOptions())
			.retryTemplate(retryTemplate)
			.build();
	}

}
