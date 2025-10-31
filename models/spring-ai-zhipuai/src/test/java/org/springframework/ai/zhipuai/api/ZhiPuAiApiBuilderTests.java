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

package org.springframework.ai.zhipuai.api;

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

class ZhiPuAiApiBuilderTests {

	private static final ApiKey TEST_API_KEY = new SimpleApiKey("test-api-key");

	private static final String TEST_BASE_URL = "https://test.bigmodel.cn/api/paas";

	private static final String TEST_COMPLETIONS_PATH = "/test/completions";

	private static final String TEST_EMBEDDINGS_PATH = "/test/embeddings";

	@Test
	void testMinimalBuilder() {
		ZhiPuAiApi api = ZhiPuAiApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testFullBuilder() {
		var headers = new HttpHeaders();
		headers.add("Custom-Header", "test-value");
		RestClient.Builder restClientBuilder = RestClient.builder();
		WebClient.Builder webClientBuilder = WebClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		ZhiPuAiApi api = ZhiPuAiApi.builder()
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
		ZhiPuAiApi api = ZhiPuAiApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
		// We can't directly test the default values as they're private fields,
		// but we know the builder succeeded with defaults
	}

	@Test
	void testMissingApiKey() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().baseUrl("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> ZhiPuAiApi.builder().baseUrl(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidHeaders() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().headers(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("headers cannot be null");
	}

	@Test
	void testInvalidCompletionsPath() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().completionsPath("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");

		assertThatThrownBy(() -> ZhiPuAiApi.builder().completionsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completionsPath cannot be null or empty");
	}

	@Test
	void testInvalidEmbeddingsPath() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().embeddingsPath("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embeddingsPath cannot be null or empty");

		assertThatThrownBy(() -> ZhiPuAiApi.builder().embeddingsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embeddingsPath cannot be null or empty");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().restClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidWebClientBuilder() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().webClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("webClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> ZhiPuAiApi.builder().responseErrorHandler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("responseErrorHandler cannot be null");
	}

	/**
	 * Tests the behavior of the {@link ZhiPuAiApi} class when using dynamic API
	 * <p>
	 * This test refers to OpenAiApiBuilderTests.
	 */
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
			ZhiPuAiApi api = ZhiPuAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{}
						""");
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			ZhiPuAiApi.ChatCompletionMessage chatCompletionMessage = new ZhiPuAiApi.ChatCompletionMessage("Hello world",
					ZhiPuAiApi.ChatCompletionMessage.Role.USER);
			ZhiPuAiApi.ChatCompletionRequest request = new ZhiPuAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "glm-4-flash", 0.8, false);
			ResponseEntity<ZhiPuAiApi.ChatCompletion> response = api.chatCompletionEntity(request);
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
			ZhiPuAiApi api = ZhiPuAiApi.builder().apiKey(() -> {
				throw new AssertionFailedError("Should not be called, API key is provided in headers");
			}).baseUrl(this.mockWebServer.url("/").toString()).build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{}
						""");
			this.mockWebServer.enqueue(mockResponse);

			ZhiPuAiApi.ChatCompletionMessage chatCompletionMessage = new ZhiPuAiApi.ChatCompletionMessage("Hello world",
					ZhiPuAiApi.ChatCompletionMessage.Role.USER);
			ZhiPuAiApi.ChatCompletionRequest request = new ZhiPuAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "glm-4-flash", 0.8, false);

			var additionalHeaders = new HttpHeaders();
			additionalHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer additional-key");
			ResponseEntity<ZhiPuAiApi.ChatCompletion> response = api.chatCompletionEntity(request, additionalHeaders);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer additional-key");
		}

		@Test
		void dynamicApiKeyWebClient() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			ZhiPuAiApi api = ZhiPuAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{}
						""".replace("\n", ""));
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			ZhiPuAiApi.ChatCompletionMessage chatCompletionMessage = new ZhiPuAiApi.ChatCompletionMessage("Hello world",
					ZhiPuAiApi.ChatCompletionMessage.Role.USER);
			ZhiPuAiApi.ChatCompletionRequest request = new ZhiPuAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "glm-4-flash", 0.8, true);
			List<ZhiPuAiApi.ChatCompletionChunk> response = api.chatCompletionStream(request).collectList().block();
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
			ZhiPuAiApi api = ZhiPuAiApi.builder().apiKey(() -> {
				throw new AssertionFailedError("Should not be called, API key is provided in headers");
			}).baseUrl(this.mockWebServer.url("/").toString()).build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{}
						""".replace("\n", ""));
			this.mockWebServer.enqueue(mockResponse);

			ZhiPuAiApi.ChatCompletionMessage chatCompletionMessage = new ZhiPuAiApi.ChatCompletionMessage("Hello world",
					ZhiPuAiApi.ChatCompletionMessage.Role.USER);
			ZhiPuAiApi.ChatCompletionRequest request = new ZhiPuAiApi.ChatCompletionRequest(
					List.of(chatCompletionMessage), "glm-4-flash", 0.8, true);
			var additionalHeaders = new HttpHeaders();
			additionalHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer additional-key");
			List<ZhiPuAiApi.ChatCompletionChunk> response = api.chatCompletionStream(request, additionalHeaders)
				.collectList()
				.block();
			assertThat(response).hasSize(1);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer additional-key");
		}

		@Test
		void dynamicApiKeyRestClientEmbeddings() throws InterruptedException {
			Queue<ApiKey> apiKeys = new LinkedList<>(List.of(new SimpleApiKey("key1"), new SimpleApiKey("key2")));
			ZhiPuAiApi api = ZhiPuAiApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{}
						""");
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			ZhiPuAiApi.EmbeddingRequest<String> request = new ZhiPuAiApi.EmbeddingRequest<>("Hello world");
			ResponseEntity<ZhiPuAiApi.EmbeddingList<ZhiPuAiApi.Embedding>> response = api.embeddings(request);
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
