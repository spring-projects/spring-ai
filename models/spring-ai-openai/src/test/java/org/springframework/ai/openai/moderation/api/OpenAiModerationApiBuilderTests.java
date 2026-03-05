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

package org.springframework.ai.openai.moderation.api;

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
import org.springframework.ai.openai.api.OpenAiModerationApi;
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
class OpenAiModerationApiBuilderTests {

	private static final ApiKey TEST_API_KEY = new SimpleApiKey("test-api-key");

	private static final String TEST_BASE_URL = "https://test.openai.com";

	private static final String TEST_MODERATION_PATH = "/v1/moderations";

	@Test
	void testMinimalBuilder() {
		OpenAiModerationApi api = OpenAiModerationApi.builder().apiKey(TEST_API_KEY).build();

		assertThat(api).isNotNull();
	}

	@Test
	void testFullBuilder() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Custom-Header", "test-value");
		RestClient.Builder restClientBuilder = RestClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		OpenAiModerationApi api = OpenAiModerationApi.builder()
			.baseUrl(TEST_BASE_URL)
			.apiKey(TEST_API_KEY)
			.moderationPath(TEST_MODERATION_PATH)
			.headers(headers)
			.restClientBuilder(restClientBuilder)
			.responseErrorHandler(errorHandler)
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testMissingApiKey() {
		assertThatThrownBy(() -> OpenAiModerationApi.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> OpenAiModerationApi.builder().baseUrl("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> OpenAiModerationApi.builder().baseUrl(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidHeaders() {
		assertThatThrownBy(() -> OpenAiModerationApi.builder().headers(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("headers cannot be null");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> OpenAiModerationApi.builder().restClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> OpenAiModerationApi.builder().responseErrorHandler(null).build())
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
			OpenAiModerationApi api = OpenAiModerationApi.builder()
				.apiKey(() -> Objects.requireNonNull(apiKeys.poll()).getValue())
				.baseUrl(this.mockWebServer.url("/").toString())
				.moderationPath(TEST_MODERATION_PATH)
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

			OpenAiModerationApi.OpenAiModerationRequest request = new OpenAiModerationApi.OpenAiModerationRequest(
					"Test");
			ResponseEntity<OpenAiModerationApi.OpenAiModerationResponse> response = api.createModeration(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key1");

			response = api.createModeration(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			recordedRequest = this.mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer key2");
		}

		@Test
		void testBuilderMethodsReturnNewInstances() {
			OpenAiModerationApi.Builder builder1 = OpenAiModerationApi.builder();
			OpenAiModerationApi.Builder builder2 = builder1.apiKey(TEST_API_KEY);
			OpenAiModerationApi.Builder builder3 = builder2.baseUrl(TEST_BASE_URL);

			assertThat(builder2).isNotNull();
			assertThat(builder3).isNotNull();

			OpenAiModerationApi api = builder3.build();
			assertThat(api).isNotNull();
		}

	}

}
