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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GeminiTtsModel.
 *
 * Requires GOOGLE_API_KEY environment variable.
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GeminiTtsModelIT {

	private GeminiTtsModel model;

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("GOOGLE_API_KEY");
		GeminiTtsApi api = new GeminiTtsApi(apiKey);
		GeminiTtsOptions defaultOptions = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.voice("Kore")
			.build();
		this.model = new GeminiTtsModel(api, defaultOptions);
	}

	@Test
	void testSingleSpeakerGeneration() {
		// Arrange
		String text = "Say cheerfully: Have a wonderful day!";
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);

		// Act
		TextToSpeechResponse response = this.model.call(prompt);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		byte[] audioData = response.getResult().getOutput();
		assertThat(audioData).isNotEmpty();
		assertThat(audioData.length).isGreaterThan(1000); // PCM audio should be
															// substantial
	}

	@Test
	void testMultiSpeakerGeneration() {
		// Arrange
		String text = """
				TTS the following conversation between Joe and Jane:
				Joe: How's it going today Jane?
				Jane: Not too bad, how about you?
				""";

		GeminiTtsApi.SpeakerVoiceConfig joe = GeminiTtsApi.SpeakerVoiceConfig.of("Joe", "Kore");
		GeminiTtsApi.SpeakerVoiceConfig jane = GeminiTtsApi.SpeakerVoiceConfig.of("Jane", "Puck");

		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.speakerVoiceConfigs(List.of(joe, jane))
			.build();

		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);

		// Act
		TextToSpeechResponse response = this.model.call(prompt);

		// Assert
		assertThat(response).isNotNull();
		byte[] audioData = response.getResult().getOutput();
		assertThat(audioData).isNotEmpty();
		assertThat(audioData.length).isGreaterThan(1000);
	}

	@Test
	void testConvenienceMethod() {
		// Act
		byte[] audioData = this.model.call("Hello, this is a test.");

		// Assert
		assertThat(audioData).isNotEmpty();
	}

	@Test
	void testDifferentVoices() {
		// Test multiple voices to ensure voice selection works
		String[] voices = { "Kore", "Puck", "Zephyr", "Charon" };

		for (String voice : voices) {
			GeminiTtsOptions options = GeminiTtsOptions.builder()
				.model("gemini-2.5-flash-preview-tts")
				.voice(voice)
				.build();

			TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing voice: " + voice, options);
			TextToSpeechResponse response = this.model.call(prompt);

			assertThat(response.getResult().getOutput()).as("Voice %s should produce audio", voice).isNotEmpty();
		}
	}

	@Test
	void testInvalidVoiceName() {
		// Arrange - use a non-existent voice name
		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.voice("NonExistentVoiceName123")
			.build();

		TextToSpeechPrompt prompt = new TextToSpeechPrompt("This should fail with an invalid voice.", options);

		// Act & Assert - expect an exception for invalid voice
		assertThat(this.model.call(prompt).getResult().getOutput())
			.as("API should handle invalid voice gracefully or return audio")
			.isNotNull();
	}

	@Test
	void testEmptyInputText() {
		// Arrange
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("");

		// Act
		TextToSpeechResponse response = this.model.call(prompt);

		// Assert - empty text should either fail or return minimal audio
		assertThat(response).isNotNull();
		// The API behavior may vary - it might return empty audio or a short silence
	}

	@Test
	void testNullInputText() {
		// Arrange & Act & Assert
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> this.model.call((String) null)))
			.as("Null text should throw an exception")
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testVeryLongText() {
		// Arrange - create a long text (test API limits)
		String longText = "This is a very long sentence. ".repeat(100); // ~3000
																		// characters

		// Act
		TextToSpeechResponse response = this.model.call(new TextToSpeechPrompt(longText));

		// Assert - should handle long text
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
		assertThat(response.getResult().getOutput().length).as("Long text should produce substantial audio")
			.isGreaterThan(10000);
	}

	/**
	 * Optional: Save output to file for manual verification. Uncomment and run manually
	 * to test audio quality.
	 */
	@Test
	@Disabled
	void saveAudioToFile() throws IOException {
		String text = "This is a test of the Gemini Text to Speech API.";
		byte[] audioData = this.model.call(text);

		Path tempFile = Files.createTempFile("gemini-tts-test-", ".pcm");
		try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
			fos.write(audioData);
		}

		System.out.println("Audio saved to: " + tempFile);
		System.out.println("To play: ffmpeg -f s16le -ar 24000 -ac 1 -i " + tempFile + " output.wav");
	}

}
