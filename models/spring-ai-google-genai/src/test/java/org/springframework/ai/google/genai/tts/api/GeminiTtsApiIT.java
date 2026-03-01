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

package org.springframework.ai.google.genai.tts.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GeminiTtsApi}.
 *
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiTtsApiIT {

	private GeminiTtsApi geminiTtsApi;

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("GEMINI_API_KEY");
		this.geminiTtsApi = new GeminiTtsApi(apiKey);
	}

	@Test
	void testSingleSpeakerGeneration() {
		var voiceConfig = new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Kore"));
		var speechConfig = new GeminiTtsApi.SpeechConfig(voiceConfig, null);
		var generationConfig = new GeminiTtsApi.GenerationConfig(List.of("AUDIO"), speechConfig);
		var content = new GeminiTtsApi.Content(List.of(new GeminiTtsApi.Part("Say cheerfully: Have a wonderful day!")));
		var request = new GeminiTtsApi.GenerateContentRequest(List.of(content), generationConfig);

		ResponseEntity<GeminiTtsApi.GenerateContentResponse> response = this.geminiTtsApi
			.generateContent("gemini-2.5-flash-preview-tts", request);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().candidates()).isNotEmpty();

		byte[] audioData = GeminiTtsApi.extractAudioData(response.getBody());
		assertThat(audioData).isNotEmpty();
		assertThat(audioData.length).isGreaterThan(1000); // Should have substantial audio
															// data
	}

	@Test
	void testMultiSpeakerGeneration() {
		var speaker1Config = GeminiTtsApi.SpeakerVoiceConfig.of("Joe", "Kore");
		var speaker2Config = GeminiTtsApi.SpeakerVoiceConfig.of("Jane", "Puck");
		var multiSpeakerConfig = new GeminiTtsApi.MultiSpeakerVoiceConfig(List.of(speaker1Config, speaker2Config));
		var speechConfig = new GeminiTtsApi.SpeechConfig(null, multiSpeakerConfig);
		var generationConfig = new GeminiTtsApi.GenerationConfig(List.of("AUDIO"), speechConfig);
		var content = new GeminiTtsApi.Content(List.of(new GeminiTtsApi.Part("""
				TTS the following conversation between Joe and Jane:
				Joe: How's it going today Jane?
				Jane: Not too bad, how about you?
				""")));
		var request = new GeminiTtsApi.GenerateContentRequest(List.of(content), generationConfig);

		ResponseEntity<GeminiTtsApi.GenerateContentResponse> response = this.geminiTtsApi
			.generateContent("gemini-2.5-flash-preview-tts", request);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().candidates()).isNotEmpty();

		byte[] audioData = GeminiTtsApi.extractAudioData(response.getBody());
		assertThat(audioData).isNotEmpty();
		assertThat(audioData.length).isGreaterThan(1000);
	}

	@Test
	void testExtractAudioData() {
		// Create a mock response
		var inlineData = new GeminiTtsApi.InlineData("audio/pcm", "SGVsbG8gV29ybGQ="); // "Hello
																						// World"
																						// base64
		var partResponse = new GeminiTtsApi.PartResponse(inlineData);
		var contentResponse = new GeminiTtsApi.ContentResponse(List.of(partResponse));
		var candidate = new GeminiTtsApi.Candidate(contentResponse);
		var response = new GeminiTtsApi.GenerateContentResponse(List.of(candidate));

		byte[] audioData = GeminiTtsApi.extractAudioData(response);

		assertThat(audioData).isNotEmpty();
		assertThat(new String(audioData)).isEqualTo("Hello World");
	}

}
