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

import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.ReplicatedVoice;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
		ReplicatedVoice replicatedVoice = new ReplicatedVoice(new byte[] { 1, 2 }, new byte[] { 3, 4 }, "audio/wav",
				"consent-signature");

		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-pro-tts")
			.voiceName("Kore")
			.replicatedVoice(replicatedVoice)
			.languageCode("en")
			.stylePrompt("Say the following in a curious way")
			.speakerVoiceConfigs(speakers)
			.multiSpeakerTurns(turns)
			.audioEncoding(GoogleGenAiAudioSpeechOptions.AudioEncoding.PCM)
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-pro-tts");
		assertThat(options.getVoiceName()).isEqualTo("Kore");
		assertThat(options.getVoice()).isEqualTo("Kore");
		assertThat(options.getReplicatedVoice()).isEqualTo(replicatedVoice);
		assertThat(options.getLanguageCode()).isEqualTo("en");
		assertThat(options.getStylePrompt()).isEqualTo("Say the following in a curious way");
		assertThat(options.getSpeakerVoiceConfigs()).isEqualTo(speakers);
		assertThat(options.getMultiSpeakerTurns()).isEqualTo(turns);
		assertThat(options.getAudioEncoding()).isEqualTo(GoogleGenAiAudioSpeechOptions.AudioEncoding.PCM);
		assertThat(options.getFormat()).isEqualTo("PCM");
	}

	@Test
	void replicatedVoiceRequiresSampleAudio() {
		assertThatThrownBy(() -> new ReplicatedVoice(null, null, null, null)).isInstanceOf(NullPointerException.class)
			.hasMessageContaining("voiceSampleAudio");
	}

	@Test
	void unsupportedPortableOptionsReturnNull() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder().voiceName("Kore").build();

		assertThat(options.getFormat()).isNull();
		assertThat(options.getSpeed()).isNull();
	}

	@Test
	void defaultConstants() {
		assertThat(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL).isEqualTo("gemini-2.5-flash-tts");
		assertThat(GoogleGenAiAudioSpeechOptions.DEFAULT_VOICE).isEqualTo("Kore");
		assertThat(GoogleGenAiAudioSpeechOptions.DEFAULT_AUDIO_ENCODING)
			.isEqualTo(GoogleGenAiAudioSpeechOptions.AudioEncoding.LINEAR16);
		assertThat(GoogleGenAiAudioSpeechOptions.OUTPUT_SAMPLE_RATE_HERTZ).isEqualTo(24000);
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
