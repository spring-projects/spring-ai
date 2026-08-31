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

package org.springframework.ai.google.genai.tts;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiAudioSpeechOptions}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiAudioSpeechOptionsTests {

	@Test
	void builderSetsAllProperties() {
		List<SpeakerVoiceConfig> speakers = List.of(new SpeakerVoiceConfig("Sam", "Kore"),
				new SpeakerVoiceConfig("Bob", "Charon"));
		List<GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn> turns = List
			.of(new GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn("Sam", "Hi"));
		List<GoogleGenAiAudioSpeechOptions.CustomPronunciation> pronunciations = List
			.of(new GoogleGenAiAudioSpeechOptions.CustomPronunciation("apple",
					GoogleGenAiAudioSpeechOptions.PhoneticEncoding.IPA, "ˈæpəl"));

		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-pro-tts")
			.voiceName("Kore")
			.languageCode("en-us")
			.stylePrompt("Say the following in a curious way.")
			.speakerVoiceConfigs(speakers)
			.ssml("<speak>Hi</speak>")
			.markup("Hi")
			.multiSpeakerTurns(turns)
			.customPronunciations(pronunciations)
			.ssmlGender(GoogleGenAiAudioSpeechOptions.SsmlVoiceGender.FEMALE)
			.customVoiceModel("custom-model")
			.voiceCloningKey("cloning-key")
			.audioEncoding(GoogleGenAiAudioSpeechOptions.AudioEncoding.MP3)
			.speed(1.25)
			.pitch(-2.0)
			.volumeGainDb(3.0)
			.sampleRateHertz(24000)
			.effectsProfileIds(List.of("headphone-class-device"))
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-pro-tts");
		assertThat(options.getVoiceName()).isEqualTo("Kore");
		assertThat(options.getVoice()).isEqualTo("Kore");
		assertThat(options.getLanguageCode()).isEqualTo("en-us");
		assertThat(options.getStylePrompt()).isEqualTo("Say the following in a curious way.");
		assertThat(options.getSpeakerVoiceConfigs()).isEqualTo(speakers);
		assertThat(options.getSsml()).isEqualTo("<speak>Hi</speak>");
		assertThat(options.getMarkup()).isEqualTo("Hi");
		assertThat(options.getMultiSpeakerTurns()).isEqualTo(turns);
		assertThat(options.getCustomPronunciations()).isEqualTo(pronunciations);
		assertThat(options.getSsmlGender()).isEqualTo(GoogleGenAiAudioSpeechOptions.SsmlVoiceGender.FEMALE);
		assertThat(options.getCustomVoiceModel()).isEqualTo("custom-model");
		assertThat(options.getVoiceCloningKey()).isEqualTo("cloning-key");
		assertThat(options.getAudioEncoding()).isEqualTo(GoogleGenAiAudioSpeechOptions.AudioEncoding.MP3);
		assertThat(options.getFormat()).isEqualTo("MP3");
		assertThat(options.getSpeed()).isEqualTo(1.25);
		assertThat(options.getPitch()).isEqualTo(-2.0);
		assertThat(options.getVolumeGainDb()).isEqualTo(3.0);
		assertThat(options.getSampleRateHertz()).isEqualTo(24000);
		assertThat(options.getEffectsProfileIds()).containsExactly("headphone-class-device");
	}

	@Test
	void unsupportedPortableOptionsReturnNull() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build();

		assertThat(options.getFormat()).isNull();
		assertThat(options.getSpeed()).isNull();
	}

	@Test
	void defaultModelConstant() {
		assertThat(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL).isEqualTo("gemini-2.5-flash-tts");
	}

	@Test
	void equalsAndHashCode() {
		GoogleGenAiAudioSpeechOptions first = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.build();
		GoogleGenAiAudioSpeechOptions second = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.build();
		GoogleGenAiAudioSpeechOptions different = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Leda")
			.build();

		assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
		assertThat(first).isNotEqualTo(different);
	}

}
