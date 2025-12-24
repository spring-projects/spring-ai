/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiTtsModelTests {

	private GeminiTtsApi mockApi;

	private GeminiTtsModel model;

	private GeminiTtsOptions defaultOptions;

	@BeforeEach
	void setUp() {
		this.mockApi = mock(GeminiTtsApi.class);
		this.defaultOptions = GeminiTtsOptions.builder().model("gemini-2.5-flash-preview-tts").voice("Kore").build();
		this.model = new GeminiTtsModel(this.mockApi, this.defaultOptions);
	}

	@Test
	void testCallWithSingleSpeaker() {
		// Arrange
		String testText = "Say cheerfully: Have a wonderful day!";
		byte[] expectedAudio = "test audio data".getBytes();
		String base64Audio = Base64.getEncoder().encodeToString(expectedAudio);

		var mockResponse = createMockResponse(base64Audio);
		when(this.mockApi.generateContent(eq("gemini-2.5-flash-preview-tts"), any()))
			.thenReturn(ResponseEntity.ok(mockResponse));

		// Act
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(testText);
		TextToSpeechResponse response = this.model.call(prompt);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		byte[] audioData = response.getResult().getOutput();
		assertThat(audioData).isEqualTo(expectedAudio);

		// Verify API was called with correct voice config
		ArgumentCaptor<GeminiTtsApi.GenerateContentRequest> requestCaptor = ArgumentCaptor
			.forClass(GeminiTtsApi.GenerateContentRequest.class);
		verify(this.mockApi).generateContent(eq("gemini-2.5-flash-preview-tts"), requestCaptor.capture());

		GeminiTtsApi.GenerateContentRequest request = requestCaptor.getValue();
		assertThat(request.contents()).hasSize(1);
		assertThat(request.contents().get(0).parts().get(0).text()).isEqualTo(testText);
		assertThat(request.generationConfig().speechConfig().voiceConfig()).isNotNull();
		assertThat(request.generationConfig().speechConfig().voiceConfig().prebuiltVoiceConfig().voiceName())
			.isEqualTo("Kore");
	}

	@Test
	void testCallWithMultiSpeaker() {
		// Arrange
		String testText = "Joe: Hello!\nJane: Hi there!";
		GeminiTtsApi.SpeakerVoiceConfig joe = new GeminiTtsApi.SpeakerVoiceConfig("Joe",
				new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Kore")));
		GeminiTtsApi.SpeakerVoiceConfig jane = new GeminiTtsApi.SpeakerVoiceConfig("Jane",
				new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Puck")));

		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.speakerVoiceConfigs(List.of(joe, jane))
			.build();

		byte[] expectedAudio = "multi-speaker audio".getBytes();
		String base64Audio = Base64.getEncoder().encodeToString(expectedAudio);

		var mockResponse = createMockResponse(base64Audio);
		when(this.mockApi.generateContent(eq("gemini-2.5-flash-preview-tts"), any()))
			.thenReturn(ResponseEntity.ok(mockResponse));

		// Act
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(testText, options);
		TextToSpeechResponse response = this.model.call(prompt);

		// Assert
		assertThat(response).isNotNull();
		byte[] audioData = response.getResult().getOutput();
		assertThat(audioData).isEqualTo(expectedAudio);

		// Verify multi-speaker config
		ArgumentCaptor<GeminiTtsApi.GenerateContentRequest> requestCaptor = ArgumentCaptor
			.forClass(GeminiTtsApi.GenerateContentRequest.class);
		verify(this.mockApi).generateContent(eq("gemini-2.5-flash-preview-tts"), requestCaptor.capture());

		GeminiTtsApi.GenerateContentRequest request = requestCaptor.getValue();
		var multiSpeakerConfig = request.generationConfig().speechConfig().multiSpeakerVoiceConfig();
		assertThat(multiSpeakerConfig).isNotNull();
		assertThat(multiSpeakerConfig.speakerVoiceConfigs()).hasSize(2);
		assertThat(multiSpeakerConfig.speakerVoiceConfigs().get(0).speaker()).isEqualTo("Joe");
		assertThat(multiSpeakerConfig.speakerVoiceConfigs().get(1).speaker()).isEqualTo("Jane");
	}

	@Test
	void testCallWithStringConvenience() {
		// Arrange
		String testText = "Hello World";
		byte[] expectedAudio = "audio bytes".getBytes();
		String base64Audio = Base64.getEncoder().encodeToString(expectedAudio);

		var mockResponse = createMockResponse(base64Audio);
		when(this.mockApi.generateContent(any(), any())).thenReturn(ResponseEntity.ok(mockResponse));

		// Act
		byte[] result = this.model.call(testText);

		// Assert
		assertThat(result).isEqualTo(expectedAudio);
	}

	@Test
	void testOptionsMerging() {
		// Arrange
		GeminiTtsOptions runtimeOptions = GeminiTtsOptions.builder()
			.voice("Puck") // Override default voice
			.build();

		byte[] expectedAudio = "audio".getBytes();
		String base64Audio = Base64.getEncoder().encodeToString(expectedAudio);

		var mockResponse = createMockResponse(base64Audio);
		when(this.mockApi.generateContent(any(), any())).thenReturn(ResponseEntity.ok(mockResponse));

		// Act
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Test", runtimeOptions);
		this.model.call(prompt);

		// Assert - verify runtime voice overrode default
		ArgumentCaptor<GeminiTtsApi.GenerateContentRequest> requestCaptor = ArgumentCaptor
			.forClass(GeminiTtsApi.GenerateContentRequest.class);
		verify(this.mockApi).generateContent(any(), requestCaptor.capture());

		assertThat(requestCaptor.getValue()
			.generationConfig()
			.speechConfig()
			.voiceConfig()
			.prebuiltVoiceConfig()
			.voiceName()).isEqualTo("Puck");
	}

	private GeminiTtsApi.GenerateContentResponse createMockResponse(String base64Audio) {
		var inlineData = new GeminiTtsApi.InlineData("audio/pcm", base64Audio);
		var part = new GeminiTtsApi.PartResponse(inlineData);
		var content = new GeminiTtsApi.ContentResponse(List.of(part));
		var candidate = new GeminiTtsApi.Candidate(content);
		return new GeminiTtsApi.GenerateContentResponse(List.of(candidate));
	}

}
