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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.scoring.ScoringRequest;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.ai.scoring.ScoringResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JinaScoringModel}.
 *
 * <p>
 * These tests verify the high-level Spring AI abstraction layer by calling the real Jina
 * AI Reranker API. A valid API key must be provided via the {@code JINA_API_KEY}
 * environment variable.
 *
 * <p>
 * To run locally:
 *
 * <pre>
 * set JINA_API_KEY=jina_YOUR_REAL_API_KEY
 * mvn test -Dtest=JinaScoringModelIT -pl models/spring-ai-jina
 * </pre>
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaScoringModelIT {

	private static final Logger logger = LoggerFactory.getLogger(JinaScoringModelIT.class);

	private JinaScoringModel scoringModel;

	@BeforeEach
	void setUp() {
		JinaScoringApi api = JinaScoringApi.builder().apiKey(System.getenv("JINA_API_KEY")).build();

		this.scoringModel = JinaScoringModel.builder()
			.jinaScoringApi(api)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.options(JinaScoringOptions.builder()
				.model(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue())
				.build())
			.build();
	}

	@Test
	void scoreSingleQueryWithMultipleDocuments() {
		// Arrange
		List<Document> documents = List.of(
				new Document(
						"Spring AI provides a unified API for integrating various AI models into Spring applications."),
				new Document("React is a JavaScript library for building user interfaces developed by Meta."),
				new Document("The Spring Framework is a comprehensive framework for enterprise Java development."),
				new Document("Python is widely used in data science and machine learning."));

		// Act - using the convenience method
		ScoringResponse response = this.scoringModel.call("What is Spring AI?", documents);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResults()).hasSize(4);

		// Verify that each result has a score assigned
		for (ScoringResult result : response.getResults()) {
			assertThat(result.getOutput()).isNotNull();
			assertThat(result.getOutput().getScore()).isNotNull();
			assertThat(result.getOutput().getScore()).isBetween(0.0, 1.0);
			assertThat(result.getOutput().getText()).isNotBlank();

			logger.info("Document: '{}...', Score: {}", result.getOutput().getText().substring(0, 30),
					result.getOutput().getScore());
		}

		// The most relevant document (Spring AI) should have the highest score
		ScoringResult topResult = response.getResult();
		assertThat(topResult).isNotNull();
		assertThat(topResult.getOutput().getText()).contains("Spring AI");

		logger.info("Top result: '{}', Score: {}", topResult.getOutput().getText(), topResult.getOutput().getScore());
	}

	@Test
	void scoreWithTopKOptionOverride() {
		// Arrange
		List<Document> documents = List.of(new Document("Spring Boot simplifies Spring application development."),
				new Document("Docker enables containerized application deployment."),
				new Document("Kubernetes automates container orchestration at scale."),
				new Document("Spring AI integrates machine learning models with Spring Boot."),
				new Document("Node.js is a server-side JavaScript runtime environment."));

		JinaScoringOptions runtimeOptions = JinaScoringOptions.builder().topK(2).build();

		ScoringRequest request = new ScoringRequest("Spring Boot development", documents, runtimeOptions);

		// Act
		ScoringResponse response = this.scoringModel.call(request);

		// Assert - only top 2 results should be returned
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(2);

		// Results should be in descending score order
		double firstScore = response.getResults().get(0).getOutput().getScore();
		double secondScore = response.getResults().get(1).getOutput().getScore();
		assertThat(firstScore).isGreaterThanOrEqualTo(secondScore);

		logger.info("Top-K=2 results:");
		response.getResults()
			.forEach(r -> logger.info("  Document: '{}...', Score: {}", r.getOutput().getText().substring(0, 25),
					r.getOutput().getScore()));
	}

	@Test
	void scoreWithScoringRequestConstructor() {
		// Arrange - tests the full ScoringRequest path (not convenvience method)
		List<Document> documents = List.of(new Document("Spring AI is built on top of the Spring Framework."),
				new Document("TensorFlow is an open-source machine learning framework by Google."));

		ScoringRequest request = new ScoringRequest("What is Spring AI?", documents, null);

		// Act
		ScoringResponse response = this.scoringModel.call(request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(2);

		// Spring AI document should score higher than TensorFlow
		ScoringResult topResult = response.getResult();
		assertThat(topResult).isNotNull();
		assertThat(topResult.getOutput().getText()).contains("Spring AI");

		logger.info("Full ScoringRequest path - Top: '{}', Score: {}", topResult.getOutput().getText(),
				topResult.getOutput().getScore());
	}

	@Test
	void scorePreservesOriginalDocumentMetadata() {
		// Arrange - verify that metadata from original documents is preserved after
		// scoring
		List<Document> documents = List.of(
				Document.builder()
					.text("Spring AI provides model integration.")
					.metadata("source", "docs.spring.io")
					.metadata("chapter", "introduction")
					.build(),
				Document.builder()
					.text("React is a UI library.")
					.metadata("source", "reactjs.org")
					.metadata("chapter", "getting-started")
					.build());

		// Act
		ScoringResponse response = this.scoringModel.call("Spring AI", documents);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(2);

		// Each result should preserve the original document's metadata
		for (ScoringResult result : response.getResults()) {
			assertThat(result.getOutput().getMetadata()).containsKey("source");
			assertThat(result.getOutput().getMetadata()).containsKey("chapter");

			logger.info("Document: '{}', Source: {}, Score: {}", result.getOutput().getText(),
					result.getOutput().getMetadata().get("source"), result.getOutput().getScore());
		}
	}

}
