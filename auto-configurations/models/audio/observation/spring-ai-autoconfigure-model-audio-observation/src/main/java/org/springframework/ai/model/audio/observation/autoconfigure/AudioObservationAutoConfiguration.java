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

package org.springframework.ai.model.audio.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelTranscriptObservationHandler;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelTextContentObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Spring AI text-to-speech and transcription model observations:
 * the opt-in handlers that log the text given to a speech model and the transcript a
 * transcription model returned. Audio is never logged.
 *
 * @author Christian Tzolov
 * @since 2.0.2
 */
@AutoConfiguration
@ConditionalOnClass({ TextToSpeechModel.class, TranscriptionModel.class })
@EnableConfigurationProperties({ AudioSpeechObservationProperties.class,
		AudioTranscriptionObservationProperties.class })
public class AudioObservationAutoConfiguration {

	private static final Log logger = LogFactory.getLog(AudioObservationAutoConfiguration.class);

	private static void logTextContentWarning() {
		logger.warn(
				"You have enabled logging out the text-to-speech text content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	private static void logTranscriptWarning() {
		logger.warn(
				"You have enabled logging out the transcription transcript with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Tracer.class)
	@ConditionalOnBean(Tracer.class)
	static class TracerPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean(value = TextToSpeechModelTextContentObservationHandler.class,
				name = "textToSpeechModelTextContentObservationHandler")
		@ConditionalOnProperty(prefix = AudioSpeechObservationProperties.CONFIG_PREFIX, name = "log-text",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<TextToSpeechModelObservationContext> textToSpeechModelTextContentObservationHandler(
				Tracer tracer) {
			logTextContentWarning();
			return new TracingAwareLoggingObservationHandler<>(new TextToSpeechModelTextContentObservationHandler(),
					tracer);
		}

		@Bean
		@ConditionalOnMissingBean(value = TranscriptionModelTranscriptObservationHandler.class,
				name = "transcriptionModelTranscriptObservationHandler")
		@ConditionalOnProperty(prefix = AudioTranscriptionObservationProperties.CONFIG_PREFIX, name = "log-transcript",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<TranscriptionModelObservationContext> transcriptionModelTranscriptObservationHandler(
				Tracer tracer) {
			logTranscriptWarning();
			return new TracingAwareLoggingObservationHandler<>(new TranscriptionModelTranscriptObservationHandler(),
					tracer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
	static class TracerNotPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = AudioSpeechObservationProperties.CONFIG_PREFIX, name = "log-text",
				havingValue = "true")
		TextToSpeechModelTextContentObservationHandler textToSpeechModelTextContentObservationHandler() {
			logTextContentWarning();
			return new TextToSpeechModelTextContentObservationHandler();
		}

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = AudioTranscriptionObservationProperties.CONFIG_PREFIX, name = "log-transcript",
				havingValue = "true")
		TranscriptionModelTranscriptObservationHandler transcriptionModelTranscriptObservationHandler() {
			logTranscriptWarning();
			return new TranscriptionModelTranscriptObservationHandler();
		}

	}

}
