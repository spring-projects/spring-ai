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

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;
import org.springframework.ai.elevenlabs.metadata.ElevenLabsAudioTranscriptionMetadata;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link ElevenLabsAudioTranscriptionModel} using mocked REST service.
 *
 * @author Alexandros Pappas
 */
@RestClientTest(ElevenLabsAudioTranscriptionModelTests.Config.class)
class ElevenLabsAudioTranscriptionModelTests {

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private TranscriptionModel transcriptionModel;

	@Test
	void testBasicTranscription() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "All your bases are belong to us",
				  "language_code": "eng",
				  "language_probability": 0.98
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.elevenlabs.io/v1/speech-to-text"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		String transcription = this.transcriptionModel.transcribe(new ClassPathResource("/speech/jfk.flac"));

		assertThat(transcription).isEqualTo("All your bases are belong to us");
		this.server.verify();
	}

	@Test
	void testCallWithDefaultOptions() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "Hello, this is a test transcription.",
				  "language_code": "eng",
				  "language_probability": 0.95
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.elevenlabs.io/v1/speech-to-text"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech/jfk.flac"));
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription.");
		this.server.verify();
	}

	@Test
	void testTranscribeWithOptions() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "Hello, this is a test transcription with options.",
				  "language_code": "eng",
				  "language_probability": 0.96
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.elevenlabs.io/v1/speech-to-text"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.languageCode("en")
			.timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD)
			.build();

		String transcription = this.transcriptionModel.transcribe(new ClassPathResource("/speech/jfk.flac"), options);

		assertThat(transcription).isEqualTo("Hello, this is a test transcription with options.");
		this.server.verify();
	}

	@Test
	void testMetadataPopulation() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "Testing metadata population",
				  "language_code": "eng",
				  "language_probability": 0.97,
				  "words": [
				    {
				      "text": "Testing",
				      "start": 0.0,
				      "end": 0.5,
				      "type": "word"
				    },
				    {
				      "text": "metadata",
				      "start": 0.5,
				      "end": 1.0,
				      "type": "word"
				    },
				    {
				      "text": "population",
				      "start": 1.0,
				      "end": 1.5,
				      "type": "word"
				    }
				  ]
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.elevenlabs.io/v1/speech-to-text"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech/jfk.flac"));
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();

		AudioTranscription transcription = response.getResult();
		assertThat(transcription.getOutput()).isEqualTo("Testing metadata population");

		// Verify metadata is populated correctly
		assertThat(transcription.getMetadata()).isInstanceOf(ElevenLabsAudioTranscriptionMetadata.class);

		ElevenLabsAudioTranscriptionMetadata metadata = (ElevenLabsAudioTranscriptionMetadata) transcription
			.getMetadata();
		assertThat(metadata.getLanguageCode()).isEqualTo("eng");
		assertThat(metadata.getLanguageProbability()).isEqualTo(0.97);
		assertThat(metadata.getWords()).isNotNull();
		assertThat(metadata.getWords()).hasSize(3);

		// Verify word-level details
		List<ElevenLabsSpeechToTextApi.Word> words = metadata.getWords();
		assertThat(words.get(0).text()).isEqualTo("Testing");
		assertThat(words.get(0).start()).isEqualTo(0.0);
		assertThat(words.get(0).end()).isEqualTo(0.5);
		assertThat(words.get(0).type()).isEqualTo(ElevenLabsSpeechToTextApi.WordType.WORD);

		assertThat(words.get(1).text()).isEqualTo("metadata");
		assertThat(words.get(1).start()).isEqualTo(0.5);
		assertThat(words.get(1).end()).isEqualTo(1.0);

		assertThat(words.get(2).text()).isEqualTo("population");
		assertThat(words.get(2).start()).isEqualTo(1.0);
		assertThat(words.get(2).end()).isEqualTo(1.5);

		this.server.verify();
	}

	@Configuration
	static class Config {

		@Bean
		public ElevenLabsSpeechToTextApi elevenLabsSpeechToTextApi(RestClient.Builder builder) {
			return ElevenLabsSpeechToTextApi.builder()
				.baseUrl("https://api.elevenlabs.io")
				.apiKey(new SimpleApiKey("test-api-key"))
				.restClientBuilder(builder)
				.responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
				.build();
		}

		@Bean
		public ElevenLabsAudioTranscriptionModel elevenLabsAudioTranscriptionModel(
				ElevenLabsSpeechToTextApi speechToTextApi) {
			return new ElevenLabsAudioTranscriptionModel(speechToTextApi);
		}

	}

}
