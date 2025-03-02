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

package org.springframework.ai.elevenlabs;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.elevenlabs.api.ElevenLabsApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link ElevenLabsTextToSpeechOptions}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
public class ElevenLabsTextToSpeechOptionsTests {

	@Test
	public void testBuilderWithAllFields() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder()
			.modelId("test-model")
			.voice("test-voice")
			.voiceId("test-voice-id") // Test both voice and voiceId
			.format("mp3_44100_128")
			.outputFormat("mp3_44100_128")
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.5, 0.8, 0.9, true, 1.2))
			.languageCode("en")
			.pronunciationDictionaryLocators(
					List.of(new ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator("dict1", "v1")))
			.seed(12345)
			.previousText("previous")
			.nextText("next")
			.previousRequestIds(List.of("req1", "req2"))
			.nextRequestIds(List.of("req3", "req4"))
			.applyTextNormalization(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON)
			.applyLanguageTextNormalization(true)
			.build();

		assertThat(options.getModelId()).isEqualTo("test-model");
		assertThat(options.getVoice()).isEqualTo("test-voice-id");
		assertThat(options.getVoiceId()).isEqualTo("test-voice-id");
		assertThat(options.getFormat()).isEqualTo("mp3_44100_128");
		assertThat(options.getOutputFormat()).isEqualTo("mp3_44100_128");
		assertThat(options.getVoiceSettings()).isNotNull();
		assertThat(options.getVoiceSettings().stability()).isEqualTo(0.5);
		assertThat(options.getVoiceSettings().similarityBoost()).isEqualTo(0.8);
		assertThat(options.getVoiceSettings().style()).isEqualTo(0.9);
		assertThat(options.getVoiceSettings().useSpeakerBoost()).isTrue();
		assertThat(options.getSpeed()).isEqualTo(1.2); // Check via getter
		assertThat(options.getLanguageCode()).isEqualTo("en");
		assertThat(options.getPronunciationDictionaryLocators()).hasSize(1);
		assertThat(options.getPronunciationDictionaryLocators().get(0).pronunciationDictionaryId()).isEqualTo("dict1");
		assertThat(options.getPronunciationDictionaryLocators().get(0).versionId()).isEqualTo("v1");
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getPreviousText()).isEqualTo("previous");
		assertThat(options.getNextText()).isEqualTo("next");
		assertThat(options.getPreviousRequestIds()).containsExactly("req1", "req2");
		assertThat(options.getNextRequestIds()).containsExactly("req3", "req4");
		assertThat(options.getApplyTextNormalization()).isEqualTo(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON);
		assertThat(options.getApplyLanguageTextNormalization()).isTrue();
	}

	@Test
	public void testCopy() {
		ElevenLabsTextToSpeechOptions original = ElevenLabsTextToSpeechOptions.builder()
			.modelId("test-model")
			.voice("test-voice")
			.format("mp3_44100_128")
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.5, 0.8, null, null, null))
			.build();

		ElevenLabsTextToSpeechOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);

		copied = ElevenLabsTextToSpeechOptions.builder().modelId("new-model").build();
		assertThat(original.getModelId()).isEqualTo("test-model");
		assertThat(copied.getModelId()).isEqualTo("new-model");
	}

	@Test
	public void testSetters() {
		ElevenLabsTextToSpeechOptions options = new ElevenLabsTextToSpeechOptions();
		options.setModelId("test-model");
		options.setVoice("test-voice");
		options.setVoiceId("test-voice-id");
		options.setOutputFormat("mp3_44100_128");
		options.setFormat("mp3_44100_128");
		options.setVoiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.5, 0.8, null, null, null));
		options.setLanguageCode("en");
		options.setPronunciationDictionaryLocators(
				List.of(new ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator("dict1", "v1")));
		options.setSeed(12345);
		options.setPreviousText("previous");
		options.setNextText("next");
		options.setPreviousRequestIds(List.of("req1", "req2"));
		options.setNextRequestIds(List.of("req3", "req4"));
		options.setApplyTextNormalization(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON);
		options.setApplyLanguageTextNormalization(true);

		assertThat(options.getModelId()).isEqualTo("test-model");
		assertThat(options.getVoice()).isEqualTo("test-voice-id");
		assertThat(options.getVoiceId()).isEqualTo("test-voice-id");
		assertThat(options.getFormat()).isEqualTo("mp3_44100_128");
		assertThat(options.getOutputFormat()).isEqualTo("mp3_44100_128");
		assertThat(options.getVoiceSettings()).isNotNull();
		assertThat(options.getVoiceSettings().stability()).isEqualTo(0.5);
		assertThat(options.getVoiceSettings().similarityBoost()).isEqualTo(0.8);
		assertThat(options.getLanguageCode()).isEqualTo("en");
		assertThat(options.getPronunciationDictionaryLocators()).hasSize(1);
		assertThat(options.getPronunciationDictionaryLocators().get(0).pronunciationDictionaryId()).isEqualTo("dict1");
		assertThat(options.getPronunciationDictionaryLocators().get(0).versionId()).isEqualTo("v1");
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getPreviousText()).isEqualTo("previous");
		assertThat(options.getNextText()).isEqualTo("next");
		assertThat(options.getPreviousRequestIds()).containsExactly("req1", "req2");
		assertThat(options.getNextRequestIds()).containsExactly("req3", "req4");
		assertThat(options.getApplyTextNormalization()).isEqualTo(ElevenLabsApi.SpeechRequest.TextNormalizationMode.ON);
		assertThat(options.getApplyLanguageTextNormalization()).isTrue();
	}

	@Test
	public void testDefaultValues() {
		ElevenLabsTextToSpeechOptions options = new ElevenLabsTextToSpeechOptions();
		assertThat(options.getModelId()).isNull();
		assertThat(options.getVoice()).isNull();
		assertThat(options.getVoiceId()).isNull();
		assertThat(options.getFormat()).isNull();
		assertThat(options.getOutputFormat()).isNull();
		assertThat(options.getSpeed()).isNull();
		assertThat(options.getVoiceSettings()).isNull();
		assertThat(options.getLanguageCode()).isNull();
		assertThat(options.getPronunciationDictionaryLocators()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.getPreviousText()).isNull();
		assertThat(options.getNextText()).isNull();
		assertThat(options.getPreviousRequestIds()).isNull();
		assertThat(options.getNextRequestIds()).isNull();
		assertThat(options.getApplyTextNormalization()).isNull();
		assertThat(options.getApplyLanguageTextNormalization()).isNull();
	}

	@Test
	public void testSetSpeed() {
		// 1. Setting speed via voiceSettings, no existing voiceSettings
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(null, null, null, null, 1.5))
			.build();
		assertThat(options.getSpeed()).isEqualTo(1.5);
		assertThat(options.getVoiceSettings()).isNotNull();
		assertThat(options.getVoiceSettings().speed()).isEqualTo(1.5);

		// 2. Setting speed via voiceSettings, existing voiceSettings
		ElevenLabsTextToSpeechOptions options2 = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, null))
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, 2.0)) // Overwrite
			.build();
		assertThat(options2.getSpeed()).isEqualTo(2.0f);
		assertThat(options2.getVoiceSettings().speed()).isEqualTo(2.0f);
		assertThat(options2.getVoiceSettings().stability()).isEqualTo(0.1);

		// 3. Setting voiceSettings with null speed, existing voiceSettings
		ElevenLabsTextToSpeechOptions options3 = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, 2.0))
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, null)) // Overwrite
			.build();
		assertThat(options3.getSpeed()).isNull();
		assertThat(options3.getVoiceSettings().speed()).isNull();
		assertThat(options3.getVoiceSettings().stability()).isEqualTo(0.1);

		// 4. Setting voiceSettings to null, no existing voiceSettings (shouldn't create
		// voiceSettings)
		ElevenLabsTextToSpeechOptions options4 = ElevenLabsTextToSpeechOptions.builder().build();
		assertThat(options4.getSpeed()).isNull();
		assertThat(options4.getVoiceSettings()).isNull();

		// 5. Setting voiceSettings directly, with speed.
		ElevenLabsTextToSpeechOptions options5 = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, 2.5))
			.build();
		assertThat(options5.getSpeed()).isEqualTo(2.5f);
		assertThat(options5.getVoiceSettings().speed()).isEqualTo(2.5f);

		// 6. Setting voiceSettings directly, without speed (speed should be null).
		ElevenLabsTextToSpeechOptions options6 = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, null))
			.build();
		assertThat(options6.getSpeed()).isNull();
		assertThat(options6.getVoiceSettings().speed()).isNull();

		// 7. Setting voiceSettings to null, after previously setting it.
		ElevenLabsTextToSpeechOptions options7 = ElevenLabsTextToSpeechOptions.builder()
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.1, 0.2, 0.3, true, 1.5))
			.voiceSettings(null)
			.build();
		assertThat(options7.getSpeed()).isNull();
		assertThat(options7.getVoiceSettings()).isNull();

		// 8. Setting speed via setSpeed method
		ElevenLabsTextToSpeechOptions options8 = ElevenLabsTextToSpeechOptions.builder().build();
		options8.setSpeed(3.0);
		assertThat(options8.getSpeed()).isEqualTo(3.0);
		assertThat(options8.getVoiceSettings()).isNotNull();
		assertThat(options8.getVoiceSettings().speed()).isEqualTo(3.0);

		// 9. Setting speed to null via setSpeed method
		options8.setSpeed(null);
		assertThat(options8.getSpeed()).isNull();
		assertThat(options8.getVoiceSettings().speed()).isNull();
	}

}
