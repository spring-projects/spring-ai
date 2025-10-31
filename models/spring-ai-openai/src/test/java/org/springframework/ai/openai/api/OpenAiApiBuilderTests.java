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

package org.springframework.ai.openai.api;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class OpenAiApiBuilderTests {

	private static final ApiKey TEST_API_KEY = new SimpleApiKey("test-api-key");

	private static final String TEST_BASE_URL = "https://test.openai.com";

	private static final String TEST_COMPLETIONS_PATH = "/test/completions";

	private static final String TEST_EMBEDDINGS_PATH = "/test/embeddings";

	@Test
	void testMinimalBuilder() {
		OpenAiApi api = OpenAiApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testFullBuilder() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Custom-Header", "test-value");
		RestClient.Builder restClientBuilder = RestClient.builder();
		WebClient.Builder webClientBuilder = WebClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		OpenAiApi api = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.headers(headers)
			.completionsPath(TEST_COMPLETIONS_PATH)
			.embeddingsPath(TEST_EMBEDDINGS_PATH)
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(errorHandler)
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testDefaultValues() {
		OpenAiApi api = OpenAiApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
		// We can't directly test the default values as they're private fields,
		// but we know the builder succeeded with defaults
	}

	@Test
	void testMissingApiKey() {
		assertThatThrownBy(() -> OpenAiApi.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> OpenAiApi.builder().baseUrl("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> OpenAiApi.builder().baseUrl(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidHeaders() {
		assertThatThrownBy(() -> OpenAiApi.builder().headers(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("headers cannot be null");
	}

	@Test
	void testInvalidCompletionsPath() {
		assertThatThrownBy(() -> OpenAiApi.builder().completionsPath("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");

		assertThatThrownBy(() -> OpenAiApi.builder().completionsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");
	}

	@Test
	void testInvalidEmbeddingsPath() {
		assertThatThrownBy(() -> OpenAiApi.builder().embeddingsPath("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embeddingsPath cannot be null or empty");

		assertThatThrownBy(() -> OpenAiApi.builder().embeddingsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embeddingsPath cannot be null or empty");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> OpenAiApi.builder().restClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidWebClientBuilder() {
		assertThatThrownBy(() -> OpenAiApi.builder().webClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("webClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> OpenAiApi.builder().responseErrorHandler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("responseErrorHandler cannot be null");
	}

	@Test
	void testBuilderWithAllCustomPaths() {
		OpenAiApi api = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.completionsPath("/custom/completions")
			.embeddingsPath("/custom/embeddings")
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testBuilderImmutability() {
		OpenAiApi.Builder builder = OpenAiApi.builder().apiKey(TEST_API_KEY).baseUrl(TEST_BASE_URL);

		OpenAiApi api1 = builder.build();
		OpenAiApi api2 = builder.build();

		assertThat(api1).isNotNull();
		assertThat(api2).isNotNull();
		assertThat(api1).isNotSameAs(api2);
	}

	@Test
	void testNullApiKeyValue() {
		assertThatThrownBy(() -> OpenAiApi.builder().apiKey((ApiKey) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey cannot be null");
	}

	@Test
	void testBuilderMethodChaining() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Test-Header", "test-value");

		OpenAiApi api = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.completionsPath(TEST_COMPLETIONS_PATH)
			.embeddingsPath(TEST_EMBEDDINGS_PATH)
			.headers(headers)
			.restClientBuilder(RestClient.builder())
			.webClientBuilder(WebClient.builder())
			.responseErrorHandler(mock(ResponseErrorHandler.class))
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testCustomHeadersPreservation() {
		HttpHeaders customHeaders = new HttpHeaders();
		customHeaders.add("X-Custom-Header", "custom-value");
		customHeaders.add("X-Organization", "org-123");
		customHeaders.add("User-Agent", "Custom-Client/1.0");

		OpenAiApi api = OpenAiApi.builder().apiKey(TEST_API_KEY).headers(customHeaders).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testComplexMultiValueHeaders() {
		HttpHeaders multiHeaders = new HttpHeaders();
		multiHeaders.add("Accept", "application/json");
		multiHeaders.add("Accept", "text/plain");
		multiHeaders.add("Cache-Control", "no-cache");
		multiHeaders.add("Cache-Control", "no-store");

		OpenAiApi api = OpenAiApi.builder().apiKey(TEST_API_KEY).headers(multiHeaders).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testPathValidationWithSlashes() {
		OpenAiApi api1 = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.completionsPath("/v1/completions")
			.embeddingsPath("/v1/embeddings")
			.build();

		OpenAiApi api2 = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.completionsPath("v1/completions")
			.embeddingsPath("v1/embeddings")
			.build();

		assertThat(api1).isNotNull();
		assertThat(api2).isNotNull();
	}

	@Test
	void testInvalidPathsWithSpecialCharacters() {
		assertThatThrownBy(() -> OpenAiApi.builder().apiKey(TEST_API_KEY).completionsPath(" ").build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testBuilderWithOnlyRequiredFields() {
		OpenAiApi api = OpenAiApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testDifferentApiKeyTypes() {
		SimpleApiKey simpleKey = new SimpleApiKey("simple-key");
		OpenAiApi api1 = OpenAiApi.builder().apiKey(simpleKey).build();
		assertThat(api1).isNotNull();

		OpenAiApi api2 = OpenAiApi.builder().apiKey(() -> "supplier-key").build();
		assertThat(api2).isNotNull();
	}

	@Test
	void testBuilderCreatesIndependentInstances() {
		HttpHeaders sharedHeaders = new HttpHeaders();
		sharedHeaders.add("X-Shared", "value");

		OpenAiApi.Builder builder = OpenAiApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.headers(sharedHeaders);

		OpenAiApi api1 = builder.build();

		// Modify the shared headers
		sharedHeaders.add("X-Modified", "new-value");

		OpenAiApi api2 = builder.build();

		// Both APIs should have the modified headers since they share the same reference
		assertThat(api1.getHeaders().containsHeader("X-Modified")).isTrue();
		assertThat(api2.getHeaders().containsHeader("X-Modified")).isTrue();
	}

	@Test
	void testMutatePreservesResponseErrorHandler() {
		ResponseErrorHandler customHandler = mock(ResponseErrorHandler.class);

		OpenAiApi original = OpenAiApi.builder().apiKey(TEST_API_KEY).responseErrorHandler(customHandler).build();

		OpenAiApi mutated = original.mutate().apiKey("new-key").build();

		assertThat(original.getResponseErrorHandler()).isSameAs(customHandler);
		assertThat(mutated.getResponseErrorHandler()).isSameAs(customHandler);
	}

	@Test
	void testMutateCreatesIndependentHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Original", "value1");

		OpenAiApi original = OpenAiApi.builder().apiKey(TEST_API_KEY).headers(headers).build();

		HttpHeaders newHeaders = new HttpHeaders();
		newHeaders.add("X-New", "value2");

		OpenAiApi mutated = original.mutate().headers(newHeaders).build();

		// Original headers should be unchanged
		assertThat(original.getHeaders().containsHeader("X-Original")).isTrue();
		assertThat(original.getHeaders().containsHeader("X-New")).isFalse();

		// Mutated should have new headers
		assertThat(mutated.getHeaders().containsHeader("X-Original")).isFalse();
		assertThat(mutated.getHeaders().containsHeader("X-New")).isTrue();
	}

	@Test
	void testNoopApiKey() {
		OpenAiApi api = OpenAiApi.builder().apiKey(new NoopApiKey()).build();

		assertThat(api).isNotNull();
		assertThat(api.getApiKey()).isInstanceOf(NoopApiKey.class);
		assertThat(api.getApiKey().getValue()).isEmpty();
	}

	@Nested
	class MockRequests {

		MockWebServer mockWebServer;

		@BeforeEach
		void setUp() throws IOException {
			this.mockWebServer = new MockWebServer();
			this.mockWebServer.start();
		}

		@AfterEach
		void tearDown() throws IOException {
			this.mockWebServer.shutdown();
		}

		@Test
		void dynamicApiKeyRestClient() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			OpenAiApi api = OpenAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "chatcmpl-12345",
							"object": "chat.completion",
							"created": 1677858242,
							"model": "gpt-3.5-turbo",
							"choices": [
								{
						    		"index": 0,
									"message": {
									"role": "assistant",
									"content": "Hello world"
									},
									"finish_reason": "stop"
								}
							],
							"usage": {
								"prompt_tokens": 10,
								"completion_tokens": 5,
								"total_tokens": 15
							}
						}
						""");
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			OpenAiApi.ChatCompletionMessage chatCompletionMessage = new OpenAiApi.ChatCompletionMessage("Hello world",
					OpenAiApi.ChatCompletionMessage.Role.USER);
			OpenAiApi.ChatCompletionRequest request = new OpenAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, false);
			ResponseEntity<OpenAiApi.ChatCompletion> response = api.chatCompletionEntity(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key1");

			response = api.chatCompletionEntity(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key2");
		}

		@Test
		void dynamicApiKeyRestClientWithAdditionalAuthorizationHeader() throws InterruptedException {
			OpenAiApi api = OpenAiApi.builder().apiKey(() -> {
				throw new AssertionFailedError("Should not be called, API key is provided in headers");
			}).baseUrl(this.mockWebServer.url("/").toString()).build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "chatcmpl-12345",
							"object": "chat.completion",
							"created": 1677858242,
							"model": "gpt-3.5-turbo",
							"choices": [
								{
						    		"index": 0,
									"message": {
									"role": "assistant",
									"content": "Hello world"
									},
									"finish_reason": "stop"
								}
							],
							"usage": {
								"prompt_tokens": 10,
								"completion_tokens": 5,
								"total_tokens": 15
							}
						}
						""");
			this.mockWebServer.enqueue(mockResponse);

			OpenAiApi.ChatCompletionMessage chatCompletionMessage = new OpenAiApi.ChatCompletionMessage("Hello world",
					OpenAiApi.ChatCompletionMessage.Role.USER);
			OpenAiApi.ChatCompletionRequest request = new OpenAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, false);

			HttpHeaders additionalHeaders = new HttpHeaders();
			additionalHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer additional-key");
			ResponseEntity<OpenAiApi.ChatCompletion> response = api.chatCompletionEntity(request, additionalHeaders);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer additional-key");
		}

		@Test
		void dynamicApiKeyWebClient() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			OpenAiApi api = OpenAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "chatcmpl-12345",
							"object": "chat.completion",
							"created": 1677858242,
							"model": "gpt-3.5-turbo",
							"choices": [
								{
						    		"index": 0,
									"message": {
									"role": "assistant",
									"content": "Hello world"
									},
									"reasoning_content": "test",
									"finish_reason": "stop"
								}
							],
							"usage": {
								"prompt_tokens": 10,
								"completion_tokens": 5,
								"total_tokens": 15
							}
						}
						""".replace("\n", ""));
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			OpenAiApi.ChatCompletionMessage chatCompletionMessage = new OpenAiApi.ChatCompletionMessage("Hello world",
					OpenAiApi.ChatCompletionMessage.Role.USER);
			OpenAiApi.ChatCompletionRequest request = new OpenAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, true);
			List<OpenAiApi.ChatCompletionChunk> response = api.chatCompletionStream(request).collectList().block();
			assertThat(response).hasSize(1);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key1");

			response = api.chatCompletionStream(request).collectList().block();
			assertThat(response).hasSize(1);

			recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key2");
		}

		@Test
		void dynamicApiKeyWebClientWithAdditionalAuthorizationHeader() throws InterruptedException {
			OpenAiApi api = OpenAiApi.builder().apiKey(() -> {
				throw new AssertionFailedError("Should not be called, API key is provided in headers");
			}).baseUrl(this.mockWebServer.url("/").toString()).build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "chatcmpl-12345",
							"object": "chat.completion",
							"created": 1677858242,
							"model": "gpt-3.5-turbo",
							"choices": [
								{
						    		"index": 0,
									"message": {
									"role": "assistant",
									"content": "Hello world"
									},
									"finish_reason": "stop"
								}
							],
							"usage": {
								"prompt_tokens": 10,
								"completion_tokens": 5,
								"total_tokens": 15
							}
						}
						""".replace("\n", ""));
			this.mockWebServer.enqueue(mockResponse);

			OpenAiApi.ChatCompletionMessage chatCompletionMessage = new OpenAiApi.ChatCompletionMessage("Hello world",
					OpenAiApi.ChatCompletionMessage.Role.USER);
			OpenAiApi.ChatCompletionRequest request = new OpenAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, true);
			HttpHeaders additionalHeaders = new HttpHeaders();
			additionalHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer additional-key");
			List<OpenAiApi.ChatCompletionChunk> response = api.chatCompletionStream(request, additionalHeaders)
				.collectList()
				.block();
			assertThat(response).hasSize(1);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer additional-key");
		}

		@Test
		void dynamicApiKeyRestClientEmbeddings() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			OpenAiApi api = OpenAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"object": "list",
							"data": [
						    	{
						    		"object": "embedding",
								"index": 0,
						       "embedding": [
						         -0.005540426,
						         0.0047363234,
						         -0.015009919,
						         -0.027093535,
						         -0.015173893,
						         0.015173893,
						         -0.017608276
						         ]
						     }
						   ],
						   "model": "text-embedding-ada-002-v2",
						   "usage": {
						     "prompt_tokens": 2,
						     "total_tokens": 2
						   }
						}
						""");
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			OpenAiApi.EmbeddingRequest<String> request = new OpenAiApi.EmbeddingRequest<>("Hello world");
			ResponseEntity<OpenAiApi.EmbeddingList<OpenAiApi.Embedding>> response = api.embeddings(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key1");

			response = api.embeddings(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key2");
		}

	}

}
