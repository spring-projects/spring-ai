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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.elevenlabs.ElevenLabsTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@link ElevenLabsVoicesApi}.
 *
 * <p>
 * These tests require a valid ElevenLabs API key to be set as an environment variable
 * named {@code ELEVEN_LABS_API_KEY}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = ElevenLabsTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ELEVEN_LABS_API_KEY", matches = ".+")
public class ElevenLabsVoicesApiIT {

	@Autowired
	private ElevenLabsVoicesApi voicesApi;

	@Test
	void getVoices() {
		ResponseEntity<ElevenLabsVoicesApi.Voices> response = this.voicesApi.getVoices();
		System.out.println("Response: " + response);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		ElevenLabsVoicesApi.Voices voicesResponse = response.getBody();

		List<ElevenLabsVoicesApi.Voice> voices = voicesResponse.voices();
		assertThat(voices).isNotNull().isNotEmpty();

		for (ElevenLabsVoicesApi.Voice voice : voices) {
			assertThat(voice.voiceId()).isNotBlank();
		}
	}

	@Test
	void getDefaultVoiceSettings() {
		ResponseEntity<ElevenLabsVoicesApi.VoiceSettings> response = this.voicesApi.getDefaultVoiceSettings();
		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();

		ElevenLabsVoicesApi.VoiceSettings settings = response.getBody();
		assertThat(settings.stability()).isNotNull();
		assertThat(settings.similarityBoost()).isNotNull();
		assertThat(settings.style()).isNotNull();
		assertThat(settings.useSpeakerBoost()).isNotNull();
	}

	@Test
	void getVoiceSettings() {
		ResponseEntity<ElevenLabsVoicesApi.Voices> voicesResponse = this.voicesApi.getVoices();
		assertThat(voicesResponse.getStatusCode().is2xxSuccessful()).isTrue();
		List<ElevenLabsVoicesApi.Voice> voices = voicesResponse.getBody().voices();
		assertThat(voices).isNotEmpty();
		String voiceId = voices.get(0).voiceId();

		ResponseEntity<ElevenLabsVoicesApi.VoiceSettings> settingsResponse = this.voicesApi.getVoiceSettings(voiceId);
		assertThat(settingsResponse.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(settingsResponse.getBody()).isNotNull();

		ElevenLabsVoicesApi.VoiceSettings settings = settingsResponse.getBody();
		assertThat(settings.stability()).isNotNull();
		assertThat(settings.similarityBoost()).isNotNull();
		assertThat(settings.style()).isNotNull();
		assertThat(settings.useSpeakerBoost()).isNotNull();
	}

	@Test
	void getVoice() {
		ResponseEntity<ElevenLabsVoicesApi.Voices> voicesResponse = this.voicesApi.getVoices();
		assertThat(voicesResponse.getStatusCode().is2xxSuccessful()).isTrue();
		List<ElevenLabsVoicesApi.Voice> voices = voicesResponse.getBody().voices();
		assertThat(voices).isNotEmpty();
		String voiceId = voices.get(0).voiceId();

		ResponseEntity<ElevenLabsVoicesApi.Voice> voiceResponse = this.voicesApi.getVoice(voiceId);
		assertThat(voiceResponse.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(voiceResponse.getBody()).isNotNull();

		ElevenLabsVoicesApi.Voice voice = voiceResponse.getBody();
		assertThat(voice.voiceId()).isEqualTo(voiceId);
		assertThat(voice.name()).isNotBlank();
	}

}
