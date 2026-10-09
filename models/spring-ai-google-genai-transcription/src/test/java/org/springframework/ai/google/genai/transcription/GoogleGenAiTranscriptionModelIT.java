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

import com.google.genai.Client;
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
 * Requires a {@code GOOGLE_GENAI_API_KEY} environment variable with a valid Gemini
 * Developer API key. The tests are skipped when the variable is not set.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiTranscriptionModelIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_GENAI_API_KEY", matches = ".+")
class GoogleGenAiTranscriptionModelIT {

	@Value("classpath:/speech-mono.wav")
	private Resource audioFile;

	@Autowired
	private GoogleGenAiTranscriptionModel transcriptionModel;

	@Test
	void transcribeAudioFile() {
		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@Test
	void transcribeWithOptions() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME)
			.prompt("Transcribe the audio verbatim.")
			.language("en-US")
			.temperature(0.0f)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();
		assertThat(response.getMetadata()).isInstanceOf(GoogleGenAiAudioTranscriptionMetadata.class);
	}

	@Test
	void streamReturnsTranscription() {
		AudioTranscriptionResponse response = this.transcriptionModel
			.stream(new AudioTranscriptionPrompt(this.audioFile))
			.blockFirst();

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		GoogleGenAiTranscriptionModel googleGenAiTranscriptionModel() {
			String apiKey = System.getenv("GOOGLE_GENAI_API_KEY");
			Client client = Client.builder().apiKey(apiKey).build();
			GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
				.builder()
				.client(client)
				.build();
			return new GoogleGenAiTranscriptionModel(connectionDetails,
					GoogleGenAiAudioTranscriptionOptions.builder().build());
		}

	}

}
