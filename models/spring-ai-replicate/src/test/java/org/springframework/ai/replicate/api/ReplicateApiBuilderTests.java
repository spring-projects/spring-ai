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

package org.springframework.ai.replicate.api;

import org.junit.jupiter.api.Test;

import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ReplicateApi.Builder}.
 *
 * @author Rene Maierhofer
 */
class ReplicateApiBuilderTests {

	private static final String TEST_API_KEY = "someKey";

	private static final String TEST_BASE_URL = "http://127.0.0.1";

	@Test
	void testBuilderWithOptions() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		WebClient.Builder webClientBuilder = WebClient.builder();
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);

		ReplicateApi api = ReplicateApi.builder()
			.apiKey(TEST_API_KEY)
			.baseUrl(TEST_BASE_URL)
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(webClientBuilder)
			.responseErrorHandler(errorHandler)
			.build();

		assertThat(api).isNotNull();
	}

	@Test
	void testBuilderWithoutApiKeyThrowsException() {
		ReplicateApi.Builder builder = ReplicateApi.builder();
		assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("apiKey");
	}

	@Test
	void testBuilderWithNullApiKeyThrowsException() {
		ReplicateApi.Builder builder = ReplicateApi.builder();
		assertThatThrownBy(() -> builder.apiKey(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ApiKey cannot be null");
	}

	@Test
	void testBuilderWithEmptyBaseUrlThrowsException() {
		ReplicateApi.Builder builder = ReplicateApi.builder();
		assertThatThrownBy(() -> builder.baseUrl("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be empty");
	}

	@Test
	void testBuilderWithNullBaseUrlThrowsException() {
		ReplicateApi.Builder builder = ReplicateApi.builder();
		assertThatThrownBy(() -> builder.baseUrl(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl cannot be empty");
	}

}
