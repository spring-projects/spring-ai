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

package org.springframework.ai.anthropic.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.opentest4j.AssertionFailedError;

/**
 * @author Filip Hrisafov
 */
public class AnthropicApiBuilderTests {

	private static final ApiKey TEST_API_KEY = new SimpleApiKey("test-api-key");

	private static final String TEST_BASE_URL = "https://test.anthropic.com";

	private static final String TEST_COMPLETIONS_PATH = "/test/completions";

	@Test
	void testMinimalBuilder() {
		AnthropicApi api = AnthropicApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testFullBuilder() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		WebClient.Builder webClientBuilder = WebClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		AnthropicApi api = AnthropicApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.completionsPath(TEST_COMPLETIONS_PATH)
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(errorHandler)
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testMissingApiKey() {
		assertThatThrownBy(() -> AnthropicApi.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> AnthropicApi.builder().baseUrl("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> AnthropicApi.builder().baseUrl(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidCompletionsPath() {
		assertThatThrownBy(() -> AnthropicApi.builder().completionsPath("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");

		assertThatThrownBy(() -> AnthropicApi.builder().completionsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> AnthropicApi.builder().restClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidWebClientBuilder() {
		assertThatThrownBy(() -> AnthropicApi.builder().webClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("webClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> AnthropicApi.builder().responseErrorHandler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("responseErrorHandler cannot be null");
	}

	@Nested
	class MockRequests {

		MockWebServer mockWebServer;

		@BeforeEach
		void setUp() throws IOException {
			mockWebServer = new MockWebServer();
			mockWebServer.start();
		}

		@AfterEach
		void tearDown() throws IOException {
			mockWebServer.shutdown();
		}

		@Test
		void dynamicApiKeyRestClient() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			AnthropicApi api = AnthropicApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY",
						 	"type": "message",
						 	"role": "assistant",
						 	"content": [],
						 	"model": "claude-opus-3-latest",
						 	"stop_reason": null,
						 	"stop_sequence": null,
							 "usage": {
						     	"input_tokens": 25,
						     	"output_tokens": 1
							}
						}
						""");
			mockWebServer.enqueue(mockResponse);
			mockWebServer.enqueue(mockResponse);

			AnthropicApi.AnthropicMessage chatCompletionMessage = new AnthropicApi.AnthropicMessage(
					List.of(new AnthropicApi.ContentBlock("Hello world")), AnthropicApi.Role.USER);
			AnthropicApi.ChatCompletionRequest request = AnthropicApi.ChatCompletionRequest.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5)
				.temperature(0.8)
				.messages(List.of(chatCompletionMessage))
				.build();
			ResponseEntity<AnthropicApi.ChatCompletionResponse> response = api.chatCompletionEntity(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("key1");

			response = api.chatCompletionEntity(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("key2");
		}

		@Test
		void dynamicApiKeyRestClientWithAdditionalApiKeyHeader() throws InterruptedException {
			AnthropicApi api = AnthropicApi.builder().apiKey(() -> {
				throw new AssertionFailedError("Should not be called, API key is provided in headers");
			}).baseUrl(mockWebServer.url("/").toString()).build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY",
						 	"type": "message",
						 	"role": "assistant",
						 	"content": [],
						 	"model": "claude-opus-3-latest",
						 	"stop_reason": null,
						 	"stop_sequence": null,
							 "usage": {
						     	"input_tokens": 25,
						     	"output_tokens": 1
							}
						}
						""");
			mockWebServer.enqueue(mockResponse);

			AnthropicApi.AnthropicMessage chatCompletionMessage = new AnthropicApi.AnthropicMessage(
					List.of(new AnthropicApi.ContentBlock("Hello world")), AnthropicApi.Role.USER);
			AnthropicApi.ChatCompletionRequest request = AnthropicApi.ChatCompletionRequest.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5)
				.temperature(0.8)
				.messages(List.of(chatCompletionMessage))
				.build();
			MultiValueMap<String, String> additionalHeaders = new LinkedMultiValueMap<>();
			additionalHeaders.add("x-api-key", "additional-key");
			ResponseEntity<AnthropicApi.ChatCompletionResponse> response = api.chatCompletionEntity(request,
					additionalHeaders);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("additional-key");
		}

		@Test
		void dynamicApiKeyWebClient() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			AnthropicApi api = AnthropicApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						{
							"type": "message_start",
							"message": {
								"id": "msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY",
								"type": "message",
								"role": "assistant",
								"content": [],
								"model": "claude-opus-4-20250514",
								"stop_reason": null,
								"stop_sequence": null,
								"usage": {
									"input_tokens": 25,
									"output_tokens": 1
								}
							}
						}
						""".replace("\n", ""));
			mockWebServer.enqueue(mockResponse);
			mockWebServer.enqueue(mockResponse);

			AnthropicApi.AnthropicMessage chatCompletionMessage = new AnthropicApi.AnthropicMessage(
					List.of(new AnthropicApi.ContentBlock("Hello world")), AnthropicApi.Role.USER);
			AnthropicApi.ChatCompletionRequest request = AnthropicApi.ChatCompletionRequest.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5)
				.temperature(0.8)
				.messages(List.of(chatCompletionMessage))
				.stream(true)
				.build();
			api.chatCompletionStream(request).collectList().block();
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("key1");

			api.chatCompletionStream(request).collectList().block();

			recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("key2");
		}

		@Test
		void dynamicApiKeyWebClientWithAdditionalApiKey() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			AnthropicApi api = AnthropicApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						{
							"type": "message_start",
							"message": {
								"id": "msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY",
								"type": "message",
								"role": "assistant",
								"content": [],
								"model": "claude-opus-4-20250514",
								"stop_reason": null,
								"stop_sequence": null,
								"usage": {
									"input_tokens": 25,
									"output_tokens": 1
								}
							}
						}
						""".replace("\n", ""));
			mockWebServer.enqueue(mockResponse);

			AnthropicApi.AnthropicMessage chatCompletionMessage = new AnthropicApi.AnthropicMessage(
					List.of(new AnthropicApi.ContentBlock("Hello world")), AnthropicApi.Role.USER);
			AnthropicApi.ChatCompletionRequest request = AnthropicApi.ChatCompletionRequest.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5)
				.temperature(0.8)
				.messages(List.of(chatCompletionMessage))
				.stream(true)
				.build();
			MultiValueMap<String, String> additionalHeaders = new LinkedMultiValueMap<>();
			additionalHeaders.add("x-api-key", "additional-key");

			api.chatCompletionStream(request, additionalHeaders).collectList().block();
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
			assertThat(recordedRequest.getHeader("x-api-key")).isEqualTo("additional-key");
		}

	}

}
