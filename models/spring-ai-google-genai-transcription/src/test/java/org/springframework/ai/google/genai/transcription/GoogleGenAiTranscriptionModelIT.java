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

package org.springframework.ai.google.genai.transcription;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GoogleGenAiTranscriptionModel}.
 * <p>
 * Requires a Google Cloud project with the Speech-to-Text API enabled and Application
 * Default Credentials available in the environment. The tests are skipped when the
 * {@code GOOGLE_CLOUD_PROJECT} environment variable is not set.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiTranscriptionModelIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
class GoogleGenAiTranscriptionModelIT {

	@Value("classpath:/speech-mono.wav")
	private Resource audioFile;

	@Autowired
	private GoogleGenAiTranscriptionModel transcriptionModel;

	@Test
	void transcribeWithChirp2() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.languageCodes(List.of("en-US"))
			.enableAutomaticPunctuation(true)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));

		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
	}

	@Test
	void transcribeWithChirp2Translation() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.languageCodes(List.of("en-US"))
			.translationTargetLanguage("es-ES")
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));

		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
	}

	@Test
	void transcribeWithChirp3Diarization() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_3")
			.languageCodes(List.of("en-US"))
			.enableSpeakerDiarization(true)
			.minSpeakerCount(1)
			.maxSpeakerCount(2)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));

		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		GoogleGenAiTranscriptionConnectionDetails connectionDetails() throws IOException {
			return GoogleGenAiTranscriptionConnectionDetails.builder()
				.projectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
				.location(System.getenv()
					.getOrDefault("GOOGLE_CLOUD_LOCATION", GoogleGenAiTranscriptionConnectionDetails.DEFAULT_LOCATION))
				.build();
		}

		@Bean
		GoogleGenAiTranscriptionModel transcriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails) {
			return new GoogleGenAiTranscriptionModel(connectionDetails,
					GoogleGenAiAudioTranscriptionOptions.builder().build());
		}

	}

}
