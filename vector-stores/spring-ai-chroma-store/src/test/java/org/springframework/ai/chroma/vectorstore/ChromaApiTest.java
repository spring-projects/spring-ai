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

package org.springframework.ai.chroma.vectorstore;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChromaApi} exception handling.
 *
 * @author Ilayaperumal Gopinathan
 */
class ChromaApiTest {

	MockWebServer mockWebServer;

	ChromaApi chromaApi;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
		this.chromaApi = ChromaApi.builder().baseUrl(this.mockWebServer.url("/").toString()).build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	void getCollectionReturnsNullOn404() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"NotFoundError\",\"message\":\"Collection [test-collection] does not exists\"}");
		this.mockWebServer.enqueue(mockResponse);

		ChromaApi.Collection result = this.chromaApi.getCollection("tenant", "database", "test-collection");

		assertThat(result).isNull();
	}

	@Test
	void getCollectionThrowsOnOtherClientError() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"BadRequest\",\"message\":\"Invalid request\"}");
		this.mockWebServer.enqueue(mockResponse);

		assertThatThrownBy(() -> this.chromaApi.getCollection("tenant", "database", "test-collection"))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Invalid request");
	}

	@Test
	void getTenantReturnsNullOn404() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"NotFoundError\",\"message\":\"Tenant [test-tenant] not found\"}");
		this.mockWebServer.enqueue(mockResponse);

		ChromaApi.Tenant result = this.chromaApi.getTenant("test-tenant");

		assertThat(result).isNull();
	}

	@Test
	void getTenantThrowsOnOtherClientError() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.FORBIDDEN.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
		this.mockWebServer.enqueue(mockResponse);

		assertThatThrownBy(() -> this.chromaApi.getTenant("test-tenant")).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Access denied");
	}

	@Test
	void getDatabaseReturnsNullOn404() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"NotFoundError\",\"message\":\"Database [test-database] not found.\"}");
		this.mockWebServer.enqueue(mockResponse);

		ChromaApi.Database result = this.chromaApi.getDatabase("tenant", "test-database");

		assertThat(result).isNull();
	}

	@Test
	void getDatabaseThrowsOnOtherClientError() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
		this.mockWebServer.enqueue(mockResponse);

		assertThatThrownBy(() -> this.chromaApi.getDatabase("tenant", "test-database"))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Authentication required");
	}

	@Test
	void getCollectionThrowsOnServerError() {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"error\":\"InternalServerError\",\"message\":\"Internal server error occurred\"}");
		this.mockWebServer.enqueue(mockResponse);

		assertThatThrownBy(() -> this.chromaApi.getCollection("tenant", "database", "test-collection"))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Internal server error occurred");
	}

}
