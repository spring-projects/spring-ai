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

package org.springframework.ai.openai.image.api;

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

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author Filip Hrisafov
 */
class OpenAiImageApiBuilderTests {

	private static final ApiKey TEST_API_KEY = new SimpleApiKey("test-api-key");

	private static final String TEST_BASE_URL = "https://test.openai.com";

	@Test
	void testMinimalBuilder() {
		OpenAiImageApi api = OpenAiImageApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testFullBuilder() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Custom-Header", "test-value");
		RestClient.Builder restClientBuilder = RestClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		OpenAiImageApi api = OpenAiImageApi.builder()
			.baseUrl(TEST_BASE_URL)
			.apiKey(TEST_API_KEY)
			.headers(headers)
			.restClientBuilder(restClientBuilder)
			.responseErrorHandler(errorHandler)
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testMissingApiKey() {
		assertThatThrownBy(() -> OpenAiImageApi.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> OpenAiImageApi.builder().baseUrl("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> OpenAiImageApi.builder().baseUrl(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidHeaders() {
		assertThatThrownBy(() -> OpenAiImageApi.builder().headers(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("headers cannot be null");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> OpenAiImageApi.builder().restClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> OpenAiImageApi.builder().responseErrorHandler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("responseErrorHandler cannot be null");
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
			OpenAiImageApi api = OpenAiImageApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.build();

			MockResponse mockResponse = new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"created": 1589478378,
							"data": [
								{
									"url": "https://upload.wikimedia.org/wikipedia/commons/4/4e/Mini_Golden_Doodle.jpg"
								}
							]
						}
						""");
			this.mockWebServer.enqueue(mockResponse);
			this.mockWebServer.enqueue(mockResponse);

			OpenAiImageApi.OpenAiImageRequest request = new OpenAiImageApi.OpenAiImageRequest("Test",
					OpenAiImageApi.ImageModel.DALL_E_3.getValue());
			ResponseEntity<OpenAiImageApi.OpenAiImageResponse> response = api.createImage(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key1");

			response = api.createImage(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key2");
		}

	}

}
