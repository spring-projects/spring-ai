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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.ScoringDocumentPostProcessor;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for JinaScoringModel combined with ScoringDocumentPostProcessor.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
public class ScoringDocumentPostProcessorIT {

	private ScoringDocumentPostProcessor postProcessor;

	@BeforeEach
	void setUp() {
		JinaScoringApi jinaScoringApi = JinaScoringApi.builder().apiKey(System.getenv("JINA_API_KEY")).build();

		JinaScoringModel scoringModel = JinaScoringModel.builder()
			.jinaScoringApi(jinaScoringApi)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.options(JinaScoringOptions.builder()
				.model(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue())
				.build())
			.build();

		this.postProcessor = ScoringDocumentPostProcessor.builder().scoringModel(scoringModel).build();
	}

	@Test
	void processReranksDocumentsCorrectly() {
		List<Document> documents = List.of(
				new Document("Spring AI provides a unified API for integrating AI models into Java applications."),
				new Document("React is a widely-used JavaScript library for building user interfaces."),
				new Document("The Spring Framework is a comprehensive framework for enterprise Java development."),
				new Document("Python is heavily utilized in data science and machine learning fields."));

		Query query = new Query("What is Spring AI?");

		List<Document> rerankedDocuments = this.postProcessor.process(query, documents);

		assertThat(rerankedDocuments).isNotNull();
		assertThat(rerankedDocuments).hasSize(4);

		// The most relevant document "Spring AI..." should be at the top after reranking
		assertThat(rerankedDocuments.get(0).getText()).contains("unified API for integrating AI models");
	}

	@Test
	void processReturnsEmptyListForEmptyInput() {
		List<Document> emptyDocuments = List.of();
		Query query = new Query("Empty query");

		List<Document> result = this.postProcessor.process(query, emptyDocuments);

		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
	}

	@Test
	void processMaintainsOriginalMetadata() {
		Document docOriginal = Document.builder()
			.text("Spring AI bridges the gap between Java and AI.")
			.metadata("category", "tech")
			.metadata("source", "docs.spring.io")
			.metadata("author", "Spring Team")
			.build();

		Document unstructuredDoc = new Document("Just some irrelevant random text.");

		List<Document> documents = List.of(unstructuredDoc, docOriginal);
		Query query = new Query("What bridges Java and AI?");

		List<Document> result = this.postProcessor.process(query, documents);

		assertThat(result).isNotNull();
		assertThat(result).hasSize(2);

		// The relevant document should be moved to the first place
		Document topDocument = result.get(0);
		assertThat(topDocument.getText()).isEqualTo(docOriginal.getText());

		// Metadata must be perfectly preserved except for the newly added score
		Map<String, Object> metadata = topDocument.getMetadata();
		assertThat(metadata).containsEntry("category", "tech");
		assertThat(metadata).containsEntry("source", "docs.spring.io");
		assertThat(metadata).containsEntry("author", "Spring Team");

		// Reranking adds a score
		assertThat(topDocument.getScore()).isNotNull();
	}

	@Test
	void emptyQueryShouldThrowException() {
		List<Document> documents = List.of(new Document("Some text"));
		Query query = new Query("");

		assertThatThrownBy(() -> this.postProcessor.process(query, documents))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
