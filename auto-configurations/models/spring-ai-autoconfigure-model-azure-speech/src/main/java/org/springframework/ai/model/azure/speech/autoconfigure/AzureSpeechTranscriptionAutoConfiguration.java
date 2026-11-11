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

package org.springframework.ai.model.azure.speech.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.azure.speech.transcription.AzureSpeechConfigFactory;
import org.springframework.ai.azure.speech.transcription.AzureSpeechTranscriptionModel;
import org.springframework.ai.azure.speech.transcription.DefaultAzureSpeechConfigFactory;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure AI Speech transcription.
 * <p>
 * This is strictly opt-in: the model is created only when
 * {@code spring.ai.model.audio.transcription} is set to {@code azure-speech}. It is not a
 * default, because the Azure Speech SDK ships platform-specific native libraries.
 * <p>
 * Azure AI Speech is a different service from Azure OpenAI audio transcription. See
 * {@code spring-ai-starter-model-azure-openai} for the latter.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass(AzureSpeechTranscriptionModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_TRANSCRIPTION_MODEL,
		havingValue = SpringAIModels.AZURE_SPEECH)
@EnableConfigurationProperties({ AzureSpeechConnectionProperties.class, AzureSpeechTranscriptionProperties.class })
public class AzureSpeechTranscriptionAutoConfiguration {

	/**
	 * Holds both the default configuration factory and the model, so that an application
	 * that supplies its own {@link TranscriptionModel} gets neither — and therefore is
	 * never asked for connection properties it does not need.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(TranscriptionModel.class)
	static class AzureSpeechTranscriptionModelConfiguration {

		/**
		 * The key-based default factory. Its construction validates the connection
		 * properties, so a missing key, a missing region and endpoint, or both of them at
		 * once fails startup with a message naming the property at fault.
		 * <p>
		 * An application that authenticates differently supplies its own
		 * {@link AzureSpeechConfigFactory} bean, in which case none of the connection
		 * properties is read or validated.
		 */
		@Bean
		@ConditionalOnMissingBean
		AzureSpeechConfigFactory azureSpeechConfigFactory(AzureSpeechConnectionProperties connectionProperties) {
			return new DefaultAzureSpeechConfigFactory(connectionProperties.toConnectionDetails());
		}

		@Bean
		AzureSpeechTranscriptionModel azureSpeechTranscriptionModel(AzureSpeechConfigFactory speechConfigFactory,
				AzureSpeechTranscriptionProperties transcriptionProperties,
				ObjectProvider<ObservationRegistry> observationRegistry,
				ObjectProvider<TranscriptionModelObservationConvention> observationConvention) {

			AzureSpeechTranscriptionModel transcriptionModel = AzureSpeechTranscriptionModel.builder()
				.speechConfigFactory(speechConfigFactory)
				.defaultOptions(transcriptionProperties.toOptions())
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.build();

			observationConvention.ifAvailable(transcriptionModel::setObservationConvention);

			return transcriptionModel;
		}

	}

}
