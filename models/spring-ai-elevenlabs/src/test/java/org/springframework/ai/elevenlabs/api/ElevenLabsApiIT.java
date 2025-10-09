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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.elevenlabs.ElevenLabsTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the {@link ElevenLabsApi}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
public class ElevenLabsApiIT {

	@Autowired
	private ElevenLabsApi elevenLabsApi;

	@Test
	public void testTextToSpeech() throws IOException {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("Hello, world!")
			.modelId("eleven_turbo_v2_5")
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		ResponseEntity<byte[]> response = this.elevenLabsApi.textToSpeech(request, validVoiceId, null);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull().isNotEmpty();
	}

	@Test
	public void testTextToSpeechWithVoiceSettings() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("Hello, with Voice settings!")
			.modelId("eleven_turbo_v2_5")
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.5, 0.7, 0.0, true, 1.0))
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		ResponseEntity<byte[]> response = this.elevenLabsApi.textToSpeech(request, validVoiceId, null);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull().isNotEmpty();
	}

	@Test
	public void testTextToSpeechWithQueryParams() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("Hello, testing query params!")
			.modelId("eleven_turbo_v2_5")
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add("optimize_streaming_latency", "2");
		queryParams.add("enable_logging", "true");
		queryParams.add("output_format", ElevenLabsApi.OutputFormat.MP3_22050_32.getValue());

		ResponseEntity<byte[]> response = this.elevenLabsApi.textToSpeech(request, validVoiceId, queryParams);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull().isNotEmpty();
	}

	@Test
	public void testTextToSpeechVoiceIdNull() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("This should fail.")
			.modelId("eleven_turbo_v2_5")
			.build();

		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> this.elevenLabsApi.textToSpeech(request, null, null));
		assertThat(exception.getMessage()).isEqualTo("voiceId must be provided. It cannot be null.");
	}

	@Test
	public void testTextToSpeechTextEmpty() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> ElevenLabsApi.SpeechRequest.builder().text("").modelId("eleven_turbo_v2_5").build());
		assertThat(exception.getMessage()).isEqualTo("text must not be empty");
	}

	// Streaming API tests

	@Test
	public void testTextToSpeechStream() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("This is a longer text to ensure multiple chunks are received through the streaming API.")
			.modelId("eleven_turbo_v2_5")
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		Flux<ResponseEntity<byte[]>> responseFlux = this.elevenLabsApi.textToSpeechStream(request, validVoiceId, null);

		// Track the number of chunks received
		AtomicInteger chunkCount = new AtomicInteger(0);

		StepVerifier.create(responseFlux).thenConsumeWhile(response -> {
			// Verify each chunk's response properties
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody()).isNotNull().isNotEmpty();
			// Count this chunk
			chunkCount.incrementAndGet();
			return true;
		}).verifyComplete();

		// Verify we received at least one chunk
		assertThat(chunkCount.get()).isPositive();
	}

	@Test
	public void testTextToSpeechStreamWithVoiceSettings() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("Hello, with Voice settings in streaming mode!")
			.modelId("eleven_turbo_v2_5")
			.voiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(0.5, 0.7, null, null, null))
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		Flux<ResponseEntity<byte[]>> responseFlux = this.elevenLabsApi.textToSpeechStream(request, validVoiceId, null);

		StepVerifier.create(responseFlux).thenConsumeWhile(response -> {
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody()).isNotNull().isNotEmpty();
			return true;
		}).verifyComplete();
	}

	@Test
	public void testTextToSpeechStreamWithQueryParams() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("Hello, testing streaming with query params!")
			.modelId("eleven_turbo_v2_5")
			.build();

		String validVoiceId = "9BWtsMINqrJLrRacOk9x";
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add("optimize_streaming_latency", "2");
		queryParams.add("enable_logging", "true");
		queryParams.add("output_format", "mp3_44100_128");

		Flux<ResponseEntity<byte[]>> responseFlux = this.elevenLabsApi.textToSpeechStream(request, validVoiceId,
				queryParams);

		StepVerifier.create(responseFlux).thenConsumeWhile(response -> {
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody()).isNotNull().isNotEmpty();
			return true;
		}).verifyComplete();
	}

	@Test
	public void testTextToSpeechStreamVoiceIdNull() {
		ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
			.text("This should fail.")
			.modelId("eleven_turbo_v2_5")
			.build();

		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> this.elevenLabsApi.textToSpeechStream(request, null, null));
		assertThat(exception.getMessage()).isEqualTo("voiceId must be provided for streaming. It cannot be null.");
	}

	@Test
	public void testTextToSpeechStreamRequestBodyNull() {
		String validVoiceId = "9BWtsMINqrJLrRacOk9x";

		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> this.elevenLabsApi.textToSpeechStream(null, validVoiceId, null));
		assertThat(exception.getMessage()).isEqualTo("requestBody can not be null.");
	}

	@Test
	public void testTextToSpeechStreamTextEmpty() {
		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			ElevenLabsApi.SpeechRequest request = ElevenLabsApi.SpeechRequest.builder()
				.text("")
				.modelId("eleven_turbo_v2_5")
				.build();

			String validVoiceId = "9BWtsMINqrJLrRacOk9x";
			this.elevenLabsApi.textToSpeechStream(request, validVoiceId, null);
		});
		assertThat(exception.getMessage()).isEqualTo("text must not be empty");
	}

}
