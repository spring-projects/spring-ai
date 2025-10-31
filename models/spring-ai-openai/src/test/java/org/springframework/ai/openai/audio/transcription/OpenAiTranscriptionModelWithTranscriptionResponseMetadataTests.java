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

import java.time.Duration;

import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionMetadata;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author Michael Lavelle
 */
@RestClientTest(OpenAiTranscriptionModelWithTranscriptionResponseMetadataTests.Config.class)
public class OpenAiTranscriptionModelWithTranscriptionResponseMetadataTests {

	private static String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private OpenAiAudioTranscriptionModel openAiTranscriptionModel;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		this.server.reset();
	}

	@Test
	void aiResponseContainsAiMetadata() {

		prepareMock();

		Resource audioFile = new ClassPathResource("speech/jfk.flac");

		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile);

		AudioTranscriptionResponse response = this.openAiTranscriptionModel.call(transcriptionRequest);

		assertThat(response).isNotNull();

		OpenAiAudioTranscriptionResponseMetadata transcriptionResponseMetadata = (OpenAiAudioTranscriptionResponseMetadata) response
			.getMetadata();

		assertThat(transcriptionResponseMetadata).isNotNull();

		RateLimit rateLimit = transcriptionResponseMetadata.getRateLimit();

		Duration expectedRequestsReset = Duration.ofDays(2L)
			.plus(Duration.ofHours(16L))
			.plus(Duration.ofMinutes(15))
			.plus(Duration.ofSeconds(29L));

		Duration expectedTokensReset = Duration.ofHours(27L)
			.plus(Duration.ofSeconds(55L))
			.plus(Duration.ofMillis(451L));

		assertThat(rateLimit).isNotNull();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(4000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(expectedRequestsReset);
		assertThat(rateLimit.getTokensLimit()).isEqualTo(725_000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(112_358L);
		assertThat(rateLimit.getTokensReset()).isEqualTo(expectedTokensReset);

		response.getResults().forEach(transcript -> {
			AudioTranscriptionMetadata transcriptionMetadata = transcript.getMetadata();
			assertThat(transcriptionMetadata).isNotNull();
		});
	}

	private void prepareMock() {

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName(), "4000");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName(), "999");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), "2d16h15m29s");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER.getName(), "725000");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER.getName(), "112358");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_RESET_HEADER.getName(), "27h55s451ms");

		this.server.expect(requestTo(StringContains.containsString("/v1/audio/transcriptions")))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
			.andRespond(withSuccess(getJson(), MediaType.APPLICATION_JSON).headers(httpHeaders));

	}

	private String getJson() {
		return """
					{
						"id": "chatcmpl-123",
						"object": "chat.completion",
						"created": 1677652288,
						"model": "gpt-3.5-turbo-0613",
						"choices": [{
							"index": 0,
							"message": {
								"role": "assistant",
								"content": "I surrender!"
							},
							"finish_reason": "stop"
							}],
							"usage": {
								"prompt_tokens": 9,
								"completion_tokens": 12,
								"total_tokens": 21
							}
						}
				""";
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiAudioApi chatCompletionApi(RestClient.Builder builder) {
			return OpenAiAudioApi.builder().apiKey(new SimpleApiKey(TEST_API_KEY)).restClientBuilder(builder).build();
		}

		@Bean
		public OpenAiAudioTranscriptionModel openAiClient(OpenAiAudioApi openAiAudioApi) {
			return new OpenAiAudioTranscriptionModel(openAiAudioApi);
		}

	}

}
