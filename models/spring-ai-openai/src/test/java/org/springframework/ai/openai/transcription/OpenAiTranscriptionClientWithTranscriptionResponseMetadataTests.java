/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.openai.transcription;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiTranscriptionClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.ai.transcription.TranscriptionRequest;
import org.springframework.ai.transcription.TranscriptionResponse;
import org.springframework.ai.transcription.metadata.TranscriptionMetadata;
import org.springframework.ai.transcription.metadata.TranscriptionResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author Michael Lavelle
 */
@RestClientTest(OpenAiTranscriptionClientWithTranscriptionResponseMetadataTests.Config.class)
public class OpenAiTranscriptionClientWithTranscriptionResponseMetadataTests {

	private static String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private OpenAiTranscriptionClient openAiTranscriptionClient;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		server.reset();
	}

	@Test
	void aiResponseContainsAiMetadata() {

		prepareMock();

		Resource audioFile = null;

		TranscriptionRequest transcriptionRequest = new TranscriptionRequest(audioFile);

		TranscriptionResponse response = this.openAiTranscriptionClient.call(transcriptionRequest);

		assertThat(response).isNotNull();

		TranscriptionResponseMetadata transcriptionResponseMetadata = response.getMetadata();

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
			TranscriptionMetadata transcriptionMetadata = transcript.getMetadata();
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

		server.expect(requestTo("/v1/audio/transcriptions"))
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
		public OpenAiApi chatCompletionApi(RestClient.Builder builder) {
			return new OpenAiApi("", TEST_API_KEY, builder);
		}

		@Bean
		public OpenAiTranscriptionClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiTranscriptionClient(openAiApi);
		}

	}

}
