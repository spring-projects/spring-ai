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

package org.springframework.ai.model.mistralai.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.mistralai.api.MistralAiAudioApi;
import org.springframework.ai.mistralai.transcription.MistralAiTranscriptionModel;
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
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Transcription {@link AutoConfiguration Auto-configuration} for Mistral AI.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiTranscriptionProperties.class })
@ConditionalOnClass(MistralAiAudioApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_TRANSCRIPTION_MODEL, havingValue = SpringAIModels.MISTRAL,
		matchIfMissing = true)
public class MistralAiTranscriptionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MistralAiTranscriptionModel mistralAiTranscriptionModel(MistralAiCommonProperties commonProperties,
			MistralAiTranscriptionProperties transcriptionProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<TranscriptionModelObservationConvention> observationConvention) {

		var apiKey = transcriptionProperties.getApiKey();
		var baseUrl = transcriptionProperties.getBaseUrl();

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonProperties.getApiKey();
		var resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonProperties.getBaseUrl();

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resolvedBaseUrl, "Mistral base URL must be set");

		var mistralAiAudioApi = MistralAiAudioApi.builder()
			.baseUrl(resolvedBaseUrl)
			.apiKey(resolvedApiKey)
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
			.build();

		var transcriptionModel = MistralAiTranscriptionModel.builder()
			.mistralAiAudioApi(mistralAiAudioApi)
			.options(transcriptionProperties.toOptions())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();

		observationConvention.ifAvailable(transcriptionModel::setObservationConvention);

		return transcriptionModel;
	}

}
