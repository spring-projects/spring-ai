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

package org.springframework.ai.elevenlabs.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.elevenlabs.ElevenLabsTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ElevenLabsSpeechToTextApi}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
class ElevenLabsSpeechToTextApiIT {

	@Autowired
	private ElevenLabsSpeechToTextApi api;

	@Test
	void testBasicTranscription() throws Exception {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");
		byte[] audioBytes = audioResource.getInputStream().readAllBytes();

		ElevenLabsSpeechToTextApi.TranscriptionRequest request = ElevenLabsSpeechToTextApi.TranscriptionRequest
			.builder()
			.file(audioBytes)
			.fileName("jfk.flac")
			.modelId("scribe_v1")
			.build();

		ResponseEntity<ElevenLabsSpeechToTextApi.SpeechToTextResponse> response = this.api.createTranscription(request);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().text()).isNotBlank();
	}

	@Test
	void testTranscriptionWithLanguageHint() throws Exception {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");
		byte[] audioBytes = audioResource.getInputStream().readAllBytes();

		ElevenLabsSpeechToTextApi.TranscriptionRequest request = ElevenLabsSpeechToTextApi.TranscriptionRequest
			.builder()
			.file(audioBytes)
			.fileName("jfk.flac")
			.modelId("scribe_v1")
			.languageCode("en")
			.build();

		ResponseEntity<ElevenLabsSpeechToTextApi.SpeechToTextResponse> response = this.api.createTranscription(request);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().text()).isNotBlank();
		assertThat(response.getBody().languageCode()).isEqualTo("eng");
	}

	@Test
	void testTranscriptionWithWordTimestamps() throws Exception {
		ClassPathResource audioResource = new ClassPathResource("speech/jfk.flac");
		byte[] audioBytes = audioResource.getInputStream().readAllBytes();

		ElevenLabsSpeechToTextApi.TranscriptionRequest request = ElevenLabsSpeechToTextApi.TranscriptionRequest
			.builder()
			.file(audioBytes)
			.fileName("jfk.flac")
			.modelId("scribe_v1")
			.timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD)
			.build();

		ResponseEntity<ElevenLabsSpeechToTextApi.SpeechToTextResponse> response = this.api.createTranscription(request);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().words()).isNotEmpty();
	}

}
