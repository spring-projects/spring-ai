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

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
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
		assertThatThrownBy(() -> {
			OpenAiApi.builder().build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("apiKey must be set");
	}

	@Test
	void testInvalidBaseUrl() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().baseUrl("").build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("baseUrl cannot be null or empty");

		assertThatThrownBy(() -> {
			OpenAiApi.builder().baseUrl(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("baseUrl cannot be null or empty");
	}

	@Test
	void testInvalidHeaders() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().headers(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("headers cannot be null");
	}

	@Test
	void testInvalidCompletionsPath() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().completionsPath("").build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("completionsPath cannot be null or empty");

		assertThatThrownBy(() -> {
			OpenAiApi.builder().completionsPath(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("completionsPath cannot be null or empty");
	}

	@Test
	void testInvalidEmbeddingsPath() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().embeddingsPath("").build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embeddingsPath cannot be null or empty");

		assertThatThrownBy(() -> {
			OpenAiApi.builder().embeddingsPath(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embeddingsPath cannot be null or empty");
	}

	@Test
	void testInvalidRestClientBuilder() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().restClientBuilder(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("restClientBuilder cannot be null");
	}

	@Test
	void testInvalidWebClientBuilder() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().webClientBuilder(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("webClientBuilder cannot be null");
	}

	@Test
	void testInvalidResponseErrorHandler() {
		assertThatThrownBy(() -> {
			OpenAiApi.builder().responseErrorHandler(null).build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("responseErrorHandler cannot be null");
	}

}
