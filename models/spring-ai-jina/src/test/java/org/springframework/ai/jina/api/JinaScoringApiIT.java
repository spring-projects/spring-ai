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

package org.springframework.ai.jina.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.jina.api.JinaScoringApi.RerankRequest;
import org.springframework.ai.jina.api.JinaScoringApi.RerankResponse;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JinaScoringApi}.
 *
 * <p>
 * These tests require a valid Jina AI API key set as the {@code JINA_API_KEY} environment
 * variable. Tests will be skipped if the key is not present.
 *
 * <p>
 * To run locally:
 *
 * <pre>
 * set JINA_API_KEY=jina_YOUR_REAL_API_KEY
 * mvn test -Dtest=JinaScoringApiIT -pl models/spring-ai-jina
 * </pre>
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaScoringApiIT {

	private static final Logger logger = LoggerFactory.getLogger(JinaScoringApiIT.class);

	private JinaScoringApi jinaScoringApi;

	@BeforeEach
	void setUp() {
		this.jinaScoringApi = JinaScoringApi.builder().apiKey(System.getenv("JINA_API_KEY")).build();
	}

	@Test
	void rerankWithBasicDocuments() {
		// Arrange
		List<String> documents = List.of(
				"Spring AI provides a unified API for AI model integration in Spring applications.",
				"React is a JavaScript library for building user interfaces.",
				"The Spring Framework is an application framework for the Java platform.",
				"Python is a high-level programming language known for its simplicity.");

		RerankRequest request = new RerankRequest(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue(),
				"What is Spring AI?", documents);

		// Act
		ResponseEntity<RerankResponse> responseEntity = this.jinaScoringApi.rerank(request);

		// Assert
		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();

		RerankResponse response = responseEntity.getBody();
		assertThat(response).isNotNull();
		assertThat(response.model()).isNotBlank();
		assertThat(response.results()).isNotEmpty();
		assertThat(response.results()).hasSize(4);

		logger.info("Model used: {}", response.model());

		// Verify that every result has a valid index and non-negative score
		response.results().forEach(result -> {
			assertThat(result.index()).isBetween(0, 3);
			assertThat(result.relevanceScore()).isBetween(0.0, 1.0);
			logger.info("Index: {}, Score: {}", result.index(), result.relevanceScore());
		});

		// The most relevant document (index 0 - "Spring AI provides...") should
		// appear first (highest score) since it directly mentions Spring AI
		assertThat(response.results().get(0).index()).isEqualTo(0);
	}

	@Test
	void rerankWithTopN() {
		// Arrange
		List<String> documents = List.of("Spring Boot simplifies microservice development.",
				"Docker is a containerization platform.", "Kubernetes orchestrates containerized applications.",
				"Spring AI integrates AI models with Spring Boot.", "Node.js is a JavaScript runtime.");

		RerankRequest request = new RerankRequest(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue(),
				"Spring Boot development", documents, 2, null, null);

		// Act
		ResponseEntity<RerankResponse> responseEntity = this.jinaScoringApi.rerank(request);

		// Assert
		RerankResponse response = responseEntity.getBody();
		assertThat(response).isNotNull();
		assertThat(response.results()).hasSize(2);

		// Both returned results should have scores, and they should be in descending
		// order
		double firstScore = response.results().get(0).relevanceScore();
		double secondScore = response.results().get(1).relevanceScore();
		assertThat(firstScore).isGreaterThanOrEqualTo(secondScore);

		logger.info("Top-2 results:");
		response.results().forEach(r -> logger.info("  Index: {}, Score: {}", r.index(), r.relevanceScore()));
	}

	@Test
	void rerankResponseIncludesUsageStatistics() {
		// Arrange
		List<String> documents = List.of("Spring Framework documentation", "Java programming guide");

		RerankRequest request = new RerankRequest(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue(),
				"Spring documentation", documents);

		// Act
		ResponseEntity<RerankResponse> responseEntity = this.jinaScoringApi.rerank(request);

		// Assert
		RerankResponse response = responseEntity.getBody();
		assertThat(response).isNotNull();
		assertThat(response.usage()).isNotNull();
		assertThat(response.usage().totalTokens()).isGreaterThan(0);

		logger.info("Total tokens used: {}", response.usage().totalTokens());
	}

}
