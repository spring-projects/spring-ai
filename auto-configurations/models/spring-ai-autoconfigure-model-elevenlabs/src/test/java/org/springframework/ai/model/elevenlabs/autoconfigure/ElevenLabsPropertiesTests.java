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

package org.springframework.ai.model.elevenlabs.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechModel;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link ElevenLabsSpeechProperties} and
 * {@link ElevenLabsConnectionProperties}.
 *
 * @author Alexandros Pappas
 * @author Issam El-atif
 * @author Sebastien Deleuze
 */
public class ElevenLabsPropertiesTests {

	@Test
	public void connectionProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.elevenlabs.api-key=YOUR_API_KEY",
				"spring.ai.elevenlabs.base-url=https://custom.api.elevenlabs.io",
				"spring.ai.elevenlabs.tts.model-id=custom-model",
				"spring.ai.elevenlabs.tts.voice=custom-voice",
				"spring.ai.elevenlabs.tts.voice-settings.stability=0.6",
				"spring.ai.elevenlabs.tts.voice-settings.similarity-boost=0.8",
				"spring.ai.elevenlabs.tts.voice-settings.style=0.2",
				"spring.ai.elevenlabs.tts.voice-settings.use-speaker-boost=false",
				"spring.ai.elevenlabs.tts.voice-settings.speed=1.5"
				// @formatter:on
		)
			.withConfiguration(
					AutoConfigurations.of(ElevenLabsAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(ElevenLabsSpeechProperties.class);
				var connectionProperties = context.getBean(ElevenLabsConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("YOUR_API_KEY");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("https://custom.api.elevenlabs.io");

				var options = speechProperties.toOptions();
				assertThat(options.getModelId()).isEqualTo("custom-model");
				assertThat(options.getVoice()).isEqualTo("custom-voice");
				assertThat(options.getVoiceSettings().stability()).isEqualTo(0.6);
				assertThat(options.getVoiceSettings().similarityBoost()).isEqualTo(0.8);
				assertThat(options.getVoiceSettings().style()).isEqualTo(0.2);
				assertThat(options.getVoiceSettings().useSpeakerBoost()).isFalse();
				assertThat(options.getSpeed()).isEqualTo(1.5f);
			});
	}

	@Test
	public void speechOptionsTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.elevenlabs.api-key=YOUR_API_KEY",
				"spring.ai.elevenlabs.tts.model-id=custom-model",
				"spring.ai.elevenlabs.tts.voice=custom-voice",
				"spring.ai.elevenlabs.tts.format=pcm_44100",
				"spring.ai.elevenlabs.tts.voice-settings.stability=0.6",
				"spring.ai.elevenlabs.tts.voice-settings.similarity-boost=0.8",
				"spring.ai.elevenlabs.tts.voice-settings.style=0.2",
				"spring.ai.elevenlabs.tts.voice-settings.use-speaker-boost=false",
				"spring.ai.elevenlabs.tts.voice-settings.speed=1.2",
				"spring.ai.elevenlabs.tts.language-code=en",
				"spring.ai.elevenlabs.tts.seed=12345",
				"spring.ai.elevenlabs.tts.previous-text=previous",
				"spring.ai.elevenlabs.tts.next-text=next",
				"spring.ai.elevenlabs.tts.apply-text-normalization=ON",
				"spring.ai.elevenlabs.tts.apply-language-text-normalization=true"
				// @formatter:on
		)
			.withConfiguration(
					AutoConfigurations.of(ElevenLabsAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var speechProperties = context.getBean(ElevenLabsSpeechProperties.class);
				var options = speechProperties.toOptions();
				assertThat(options.getModelId()).isEqualTo("custom-model");
				assertThat(options.getVoice()).isEqualTo("custom-voice");
				assertThat(options.getFormat()).isEqualTo("pcm_44100");
				assertThat(options.getVoiceSettings().stability()).isEqualTo(0.6);
				assertThat(options.getVoiceSettings().similarityBoost()).isEqualTo(0.8);
				assertThat(options.getVoiceSettings().style()).isEqualTo(0.2);
				assertThat(options.getVoiceSettings().useSpeakerBoost()).isFalse();
				assertThat(options.getVoiceSettings().speed()).isEqualTo(1.2);
				assertThat(options.getSpeed()).isEqualTo(1.2);
				assertThat(options.getLanguageCode()).isEqualTo("en");
				assertThat(options.getSeed()).isEqualTo(12345);
				assertThat(options.getPreviousText()).isEqualTo("previous");
				assertThat(options.getNextText()).isEqualTo("next");
				assertThat(options.getApplyTextNormalization())
					.isEqualTo(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON);
				assertThat(options.getApplyLanguageTextNormalization()).isTrue();
			});
	}

	@Test
	public void speechActivation() {

		// It is enabled by default
		new ApplicationContextRunner().withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY")
			.withConfiguration(
					AutoConfigurations.of(ElevenLabsAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isNotEmpty();
			});

		// Explicitly enable the text-to-speech autoconfiguration.
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY", "spring.ai.model.audio.speech=elevenlabs")
			.withConfiguration(
					AutoConfigurations.of(ElevenLabsAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isNotEmpty();
			});

		// Explicitly disable the text-to-speech autoconfiguration.
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY", "spring.ai.model.audio.speech=none")
			.withConfiguration(
					AutoConfigurations.of(ElevenLabsAutoConfiguration.class, RestClientAutoConfiguration.class,
							SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isEmpty();
			});
	}

}
