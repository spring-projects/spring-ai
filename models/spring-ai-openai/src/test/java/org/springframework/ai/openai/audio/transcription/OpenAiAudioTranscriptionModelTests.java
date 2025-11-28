/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.audio.transcription;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(OpenAiAudioTranscriptionModelTests.Config.class)
class OpenAiAudioTranscriptionModelTests {

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private TranscriptionModel transcriptionModel;

	@Test
	void transcribeRequestReturnsResponseCorrectly() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "All your bases are belong to us"
				}
				""".stripIndent();
		// CHECKSTYLE:ON
		this.server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		String transcription = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"));

		assertThat(transcription).isEqualTo("All your bases are belong to us");
		this.server.verify();
	}

	@Test
	void callWithDefaultOptions() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "Hello, this is a test transcription."
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription.");
		this.server.verify();
	}

	@Test
	void transcribeWithOptions() {
		// CHECKSTYLE:OFF
		String mockResponse = """
				{
				  "text": "Hello, this is a test transcription with options."
				}
				""".stripIndent();
		// CHECKSTYLE:ON

		this.server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.responseFormat(TranscriptResponseFormat.JSON)
			.build();

		String transcription = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(transcription).isEqualTo("Hello, this is a test transcription with options.");
		this.server.verify();
	}

	@Configuration
	static class Config {

		@Bean
		public OpenAiAudioApi openAiAudioApi(RestClient.Builder builder) {
			return new OpenAiAudioApi("https://api.openai.com", new SimpleApiKey("test-api-key"), new HttpHeaders(),
					builder, WebClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
		}

		@Bean
		public OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel(OpenAiAudioApi audioApi) {
			return new OpenAiAudioTranscriptionModel(audioApi);
		}

	}

}
