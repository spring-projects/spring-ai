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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;
import org.springframework.ai.elevenlabs.metadata.ElevenLabsAudioTranscriptionMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ElevenLabsAudioTranscriptionModel}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
class ElevenLabsAudioTranscriptionModelIT {

	@Autowired
	private ElevenLabsAudioTranscriptionModel transcriptionModel;

	@Test
	void testBasicTranscription() {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");

		AudioTranscriptionResponse response = this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void testTranscriptionWithOptions() {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");

		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.languageCode("en")
			.timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(audioResource, options));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();

		// Verify metadata
		ElevenLabsAudioTranscriptionMetadata metadata = (ElevenLabsAudioTranscriptionMetadata) response.getResult()
			.getMetadata();
		assertThat(metadata.getLanguageCode()).isEqualTo("eng");
		assertThat(metadata.getWords()).isNotEmpty();
	}

	@Test
	void testConvenienceTranscribeMethod() {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");

		String text = this.transcriptionModel.transcribe(audioResource);

		assertThat(text).isNotBlank();
	}

	@Test
	void testTranscriptionWithDiarization() {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");

		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.diarize(true)
			.numSpeakers(1)
			.timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(audioResource, options));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();

		ElevenLabsAudioTranscriptionMetadata metadata = (ElevenLabsAudioTranscriptionMetadata) response.getResult()
			.getMetadata();
		assertThat(metadata.getWords()).isNotEmpty();
	}

}
