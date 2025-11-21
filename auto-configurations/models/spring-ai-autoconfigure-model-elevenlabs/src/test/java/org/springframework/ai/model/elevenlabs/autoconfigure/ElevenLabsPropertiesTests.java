/*
 * Copyright 2025-2025 the original author or authors.
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
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link ElevenLabsSpeechProperties} and
 * {@link ElevenLabsConnectionProperties}.
 *
 * @author Alexandros Pappas
 * @author Issam El-atif
 */
public class ElevenLabsPropertiesTests {

	@Test
	public void connectionProperties() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.elevenlabs.api-key=YOUR_API_KEY",
				"spring.ai.elevenlabs.base-url=https://custom.api.elevenlabs.io",
				"spring.ai.elevenlabs.tts.options.model-id=custom-model",
				"spring.ai.elevenlabs.tts.options.voice=custom-voice",
				"spring.ai.elevenlabs.tts.options.voice-settings.stability=0.6",
				"spring.ai.elevenlabs.tts.options.voice-settings.similarity-boost=0.8",
				"spring.ai.elevenlabs.tts.options.voice-settings.style=0.2",
				"spring.ai.elevenlabs.tts.options.voice-settings.use-speaker-boost=false",
				"spring.ai.elevenlabs.tts.options.voice-settings.speed=1.5"
				// @formatter:on
		).withConfiguration(SpringAiTestAutoConfigurations.of(ElevenLabsAutoConfiguration.class)).run(context -> {
			var speechProperties = context.getBean(ElevenLabsSpeechProperties.class);
			var connectionProperties = context.getBean(ElevenLabsConnectionProperties.class);

			assertThat(connectionProperties.getApiKey()).isEqualTo("YOUR_API_KEY");
			assertThat(connectionProperties.getBaseUrl()).isEqualTo("https://custom.api.elevenlabs.io");

			assertThat(speechProperties.getOptions().getModelId()).isEqualTo("custom-model");
			assertThat(speechProperties.getOptions().getVoice()).isEqualTo("custom-voice");
			assertThat(speechProperties.getOptions().getVoiceSettings().stability()).isEqualTo(0.6);
			assertThat(speechProperties.getOptions().getVoiceSettings().similarityBoost()).isEqualTo(0.8);
			assertThat(speechProperties.getOptions().getVoiceSettings().style()).isEqualTo(0.2);
			assertThat(speechProperties.getOptions().getVoiceSettings().useSpeakerBoost()).isFalse();
			assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(1.5f);
		});
	}

	@Test
	public void speechOptionsTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.elevenlabs.api-key=YOUR_API_KEY",
				"spring.ai.elevenlabs.tts.options.model-id=custom-model",
				"spring.ai.elevenlabs.tts.options.voice=custom-voice",
				"spring.ai.elevenlabs.tts.options.format=pcm_44100",
				"spring.ai.elevenlabs.tts.options.voice-settings.stability=0.6",
				"spring.ai.elevenlabs.tts.options.voice-settings.similarity-boost=0.8",
				"spring.ai.elevenlabs.tts.options.voice-settings.style=0.2",
				"spring.ai.elevenlabs.tts.options.voice-settings.use-speaker-boost=false",
				"spring.ai.elevenlabs.tts.options.voice-settings.speed=1.2",
				"spring.ai.elevenlabs.tts.options.language-code=en",
				"spring.ai.elevenlabs.tts.options.seed=12345",
				"spring.ai.elevenlabs.tts.options.previous-text=previous",
				"spring.ai.elevenlabs.tts.options.next-text=next",
				"spring.ai.elevenlabs.tts.options.apply-text-normalization=ON",
				"spring.ai.elevenlabs.tts.options.apply-language-text-normalization=true"
				// @formatter:on
		).withConfiguration(SpringAiTestAutoConfigurations.of(ElevenLabsAutoConfiguration.class)).run(context -> {
			var speechProperties = context.getBean(ElevenLabsSpeechProperties.class);

			assertThat(speechProperties.getOptions().getModelId()).isEqualTo("custom-model");
			assertThat(speechProperties.getOptions().getVoice()).isEqualTo("custom-voice");
			assertThat(speechProperties.getOptions().getFormat()).isEqualTo("pcm_44100");
			assertThat(speechProperties.getOptions().getVoiceSettings().stability()).isEqualTo(0.6);
			assertThat(speechProperties.getOptions().getVoiceSettings().similarityBoost()).isEqualTo(0.8);
			assertThat(speechProperties.getOptions().getVoiceSettings().style()).isEqualTo(0.2);
			assertThat(speechProperties.getOptions().getVoiceSettings().useSpeakerBoost()).isFalse();
			assertThat(speechProperties.getOptions().getVoiceSettings().speed()).isEqualTo(1.2);
			assertThat(speechProperties.getOptions().getSpeed()).isEqualTo(1.2);
			assertThat(speechProperties.getOptions().getLanguageCode()).isEqualTo("en");
			assertThat(speechProperties.getOptions().getSeed()).isEqualTo(12345);
			assertThat(speechProperties.getOptions().getPreviousText()).isEqualTo("previous");
			assertThat(speechProperties.getOptions().getNextText()).isEqualTo("next");
			assertThat(speechProperties.getOptions().getApplyTextNormalization())
				.isEqualTo(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON);
			assertThat(speechProperties.getOptions().getApplyLanguageTextNormalization()).isTrue();
		});
	}

	@Test
	public void speechActivation() {

		// It is enabled by default
		new ApplicationContextRunner().withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ElevenLabsAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isNotEmpty();
			});

		// Explicitly enable the text-to-speech autoconfiguration.
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY", "spring.ai.model.audio.speech=elevenlabs")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ElevenLabsAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isNotEmpty();
			});

		// Explicitly disable the text-to-speech autoconfiguration.
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.elevenlabs.api-key=YOUR_API_KEY", "spring.ai.model.audio.speech=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ElevenLabsAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(ElevenLabsSpeechProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(ElevenLabsTextToSpeechModel.class)).isEmpty();
			});
	}

}
