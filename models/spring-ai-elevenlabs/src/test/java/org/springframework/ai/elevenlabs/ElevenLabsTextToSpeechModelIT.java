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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the {@link ElevenLabsTextToSpeechModel}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
public class ElevenLabsTextToSpeechModelIT {

	private static final String VOICE_ID = "9BWtsMINqrJLrRacOk9x";

	@Autowired
	private ElevenLabsTextToSpeechModel textToSpeechModel;

	@Test
	void textToSpeechWithVoiceTest() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().voice(VOICE_ID).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello, world!", options);
		TextToSpeechResponse response = this.textToSpeechModel.call(prompt);

		assertThat(response).isNotNull();
		List<Speech> results = response.getResults();
		assertThat(results).hasSize(1);
		Speech speech = results.get(0);
		assertThat(speech.getOutput()).isNotEmpty();
	}

	@Test
	void textToSpeechStreamWithVoiceTest() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().voice(VOICE_ID).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(
				"Hello, world! This is a test of streaming speech synthesis.", options);
		Flux<TextToSpeechResponse> responseFlux = this.textToSpeechModel.stream(prompt);

		List<TextToSpeechResponse> responses = responseFlux.collectList().block();
		assertThat(responses).isNotNull().isNotEmpty();

		responses.forEach(response -> {
			assertThat(response).isNotNull();
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		});
	}

	@Test
	void invalidVoiceId() {
		ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder()
			.model("eleven_turbo_v2_5")
			.voiceId("invalid-voice-id")
			.outputFormat(ElevenLabsApi.OutputFormat.MP3_44100_128.getValue())
			.build();

		TextToSpeechPrompt speechPrompt = new TextToSpeechPrompt("Hello, this is a text-to-speech example.", options);

		assertThatThrownBy(() -> this.textToSpeechModel.call(speechPrompt)).isInstanceOf(NonTransientAiException.class)
			.hasMessageContaining("An invalid ID has been received: 'invalid-voice-id'");
	}

	@Test
	void emptyInputText() {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("");
		assertThatThrownBy(() -> this.textToSpeechModel.call(prompt)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("A voiceId must be specified in the ElevenLabsSpeechOptions.");
	}

}
