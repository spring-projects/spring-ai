/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.jina;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.document.Document;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.scoring.ScoringRequest;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JinaScoringModel}.
 *
 * @author Wongi Kim
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class JinaScoringModelTests {

	private MockWebServer mockWebServer;

	private JinaScoringApi jinaScoringApi;

	private JinaScoringModel jinaScoringModel;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();

		this.jinaScoringApi = JinaScoringApi.builder()
			.baseUrl(this.mockWebServer.url("/").toString())
			.apiKey("TEST_API_KEY")
			.build();

		this.jinaScoringModel = JinaScoringModel.builder()
			.jinaScoringApi(this.jinaScoringApi)
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.options(JinaScoringOptions.builder()
				.model(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue())
				.build())
			.build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	void rerankDocumentsSuccessfully() throws Exception {
		// @formatter:off
		// Arrange
		String expectedResponse = """
				{
					"model": "jina-reranker-v2-base-multilingual",
					"usage": {
						"total_tokens": 150
					},
					"results": [
						{
							"index": 2,
							"document": {
								"text": "The Spring Framework is an application framework and inversion of control container for the Java platform."
							},
							"relevance_score": 0.89123
						},
						{
							"index": 0,
							"document": {
								"text": "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications."
							},
							"relevance_score": 0.45789
						},
						{
							"index": 1,
							"document": {
								"text": "React is a free and open-source front-end JavaScript library."
							},
							"relevance_score": 0.12345
						}
					]
				}
				""";
		// @formatter:on

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody(expectedResponse));

		List<Document> documents = List.of(
				new Document(
						"Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications."),
				new Document("React is a free and open-source front-end JavaScript library."), new Document(
						"The Spring Framework is an application framework and inversion of control container for the Java platform."));

		ScoringRequest request = new ScoringRequest("What is Spring Framework?", documents, null);

		// Act
		ScoringResponse response = this.jinaScoringModel.call(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(3);

		// The results should be returned in the order provided by the API (descending
		// relevance score)
		assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo(
				"The Spring Framework is an application framework and inversion of control container for the Java platform.");
		assertThat(response.getResults().get(0).getOutput().getScore()).isEqualTo(0.89123);

		assertThat(response.getResults().get(1).getOutput().getText())
			.isEqualTo("Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications.");
		assertThat(response.getResults().get(1).getOutput().getScore()).isEqualTo(0.45789);

		assertThat(response.getResults().get(2).getOutput().getText())
			.isEqualTo("React is a free and open-source front-end JavaScript library.");
		assertThat(response.getResults().get(2).getOutput().getScore()).isEqualTo(0.12345);

		// Verify request
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).isEqualTo("/v1/rerank");
		assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer TEST_API_KEY");

		String requestBody = recordedRequest.getBody().readUtf8();
		assertThat(requestBody).contains("What is Spring Framework?");
		assertThat(requestBody).contains("jina-reranker-v2-base-multilingual");
		assertThat(requestBody).contains("Spring Boot");
		assertThat(requestBody).contains("React");
	}

	@Test
	void mergeOptionsCorrectly() throws Exception {
		// Arrange
		// @formatter:off
		String expectedResponse = """
				{
					"model": "jina-colbert-v1-en",
					"results": []
				}
				""";
		// @formatter:on

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody(expectedResponse));

		List<Document> documents = List.of(new Document("doc1"));

		// Create runtime options with model override
		JinaScoringOptions runtimeOptions = JinaScoringOptions.builder()
			.model(JinaScoringApi.Model.JINA_COLBERT_V1_EN.getValue())
			.topK(2)
			.truncation(true)
			.build();

		ScoringRequest request = new ScoringRequest("Test query", documents, runtimeOptions);

		// Act
		this.jinaScoringModel.call(request);

		// Assert
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();

		// Verify that runtime options were used instead of default options
		assertThat(requestBody).contains("jina-colbert-v1-en");
		assertThat(requestBody).contains("\"top_n\":2");
		assertThat(requestBody).contains("\"truncation\":true");
	}

	@Test
	void apiErrorShouldThrowException() {
		// Arrange
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(401)
			.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("{\"detail\": \"Incorrect API key provided\"}"));

		List<Document> documents = List.of(new Document("test"));
		ScoringRequest request = new ScoringRequest("query", documents, null);

		// Act & Assert
		assertThatThrownBy(() -> this.jinaScoringModel.call(request))
			.isInstanceOf(org.springframework.ai.retry.NonTransientAiException.class)
			.hasMessageContaining("401");
	}

	@Test
	void emptyDocumentListShouldThrowException() {
		assertThatThrownBy(() -> this.jinaScoringModel.call(new ScoringRequest("query", List.of(), null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void serverErrorTriggersRetry() throws Exception {
		// Arrange: Two 500 errors followed by a 200 success
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

		// @formatter:off
		String expectedResponse = """
				{
					"model": "jina-reranker-v2-base-multilingual",
					"results": [
						{
							"index": 0,
							"document": {
								"text": "Success after retry!"
							},
							"relevance_score": 0.99
						}
					]
				}
				""";
		// @formatter:on
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody(expectedResponse));

		List<Document> documents = List.of(new Document("Success after retry!"));
		ScoringRequest request = new ScoringRequest("test", documents, null);

		// Act
		ScoringResponse response = this.jinaScoringModel.call(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		// Verify that exactly 3 requests were made (2 failed, 1 succeeded)
		assertThat(this.mockWebServer.getRequestCount()).isEqualTo(3);
	}

}
