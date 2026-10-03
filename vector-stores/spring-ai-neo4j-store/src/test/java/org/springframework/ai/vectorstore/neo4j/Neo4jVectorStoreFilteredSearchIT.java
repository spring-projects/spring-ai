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

package org.springframework.ai.vectorstore.neo4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ClientException;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Characterizes metadata post-filtering with fixed vectors and no external embedding
 * service.
 */
@Testcontainers
class Neo4jVectorStoreFilteredSearchIT {

	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(Neo4jImage.DEFAULT_IMAGE).withRandomPassword();

	private static Driver driver;

	private static Neo4jVectorStore vectorStore;

	@BeforeAll
	static void setUp() {
		driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(),
				AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		given(embeddingModel.dimensions()).willReturn(3);
		given(embeddingModel.embed("query")).willReturn(new float[] { 1.0f, 0.0f, 0.0f });
		vectorStore = Neo4jVectorStore.builder(driver, embeddingModel)
			.label("FilteredSearchDocument")
			.indexName("filtered-search-index")
			.embeddingDimension(3)
			.build();

		try (var session = driver.session()) {
			// The closest five vectors are frontend documents. Ten farther vectors are
			// backend documents, so there are more eligible documents than topK = 5.
			session.run("""
					UNWIND range(1, 5) AS i
					CREATE (:FilteredSearchDocument {
						id: 'f' + toString(i), text: 'frontend', `metadata.category`: 'frontend',
						embedding: [1.0, i / 100.0, 0.0]
					})
					""").consume();
			session.run("""
					UNWIND range(0, 9) AS i
					CREATE (:FilteredSearchDocument {
						id: 'b' + toString(i), text: 'backend', `metadata.category`: 'backend',
						embedding: [1.0, 0.5 + i / 100.0, 0.0]
					})
					""").consume();
			session.run("""
					CREATE VECTOR INDEX `filtered-search-index`
					FOR (n:FilteredSearchDocument) ON (n.embedding)
					OPTIONS {indexConfig: {
						`vector.dimensions`: 3,
						`vector.similarity_function`: 'cosine',
						`vector.quantization.enabled`: false
					}}
					""").consume();
			session.run("CALL db.awaitIndexes(120)").consume();
		}
	}

	@AfterAll
	static void tearDown() {
		if (driver != null) {
			driver.close();
		}
	}

	@Test
	void filtersOnlyTheGlobalTopKCandidates() {
		var request = SearchRequest.builder().query("query").topK(5).similarityThreshold(0.0).build();
		var candidates = vectorStore.similaritySearch(request);
		assertThat(candidates).hasSize(5)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("category", "frontend"));

		try (var session = driver.session()) {
			long eligibleCount = session.run("""
					MATCH (n:FilteredSearchDocument)
					WHERE n.`metadata.category` = 'backend'
						AND vector.similarity.cosine(n.embedding, [1.0, 0.0, 0.0]) >= 0.0
					RETURN count(n) AS eligible
					""").single().get("eligible").asLong();
			assertThat(eligibleCount).isEqualTo(10);
		}

		// This records the existing post-filter behavior: eligible documents outside
		// the global candidate pool do not replenish the result list.
		var filtered = vectorStore
			.similaritySearch(SearchRequest.from(request).filterExpression("category == 'backend'").build());
		assertThat(filtered).isEmpty();
	}

	@Test
	void returnsMatchingCandidatesWithScoresAndMetadata() {
		var results = vectorStore.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(3)
			.similarityThreshold(0.0)
			.filterExpression("category == 'frontend'")
			.build());

		assertThat(results).hasSize(3).allSatisfy(document -> {
			assertThat(document.getId()).startsWith("f");
			assertThat(document.getText()).isEqualTo("frontend");
			assertThat(document.getMetadata()).containsEntry("category", "frontend");
			assertThat(document.getScore()).isNotNull().isBetween(0.99, 1.0);
			double distance = ((Number) document.getMetadata().get(DocumentMetadata.DISTANCE.value())).doubleValue();
			assertThat(distance).isCloseTo(1.0 - document.getScore(), within(0.000001));
		});
	}

	@Test
	void appliesSimilarityThresholdToMatchingCandidates() {
		var results = vectorStore.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(5)
			.similarityThreshold(1.0)
			.filterExpression("category == 'frontend'")
			.build());

		assertThat(results).isEmpty();
	}

	@Test
	void searchStrategyRequiresANewerServer() {
		var model = mock(EmbeddingModel.class);
		given(model.dimensions()).willReturn(3);
		given(model.embed("query")).willReturn(new float[] { 1.0f, 0.0f, 0.0f });
		var search = Neo4jVectorStore.builder(driver, model)
			.label("FilteredSearchDocument")
			.indexName("filtered-search-index")
			.searchStrategy(Neo4jVectorStore.SearchStrategy.SEARCH)
			.build();
		assertThatThrownBy(() -> search.similaritySearch(SearchRequest.builder().query("query").build()))
			.isInstanceOf(ClientException.class)
			.hasMessageContaining("25");
	}

	@Test
	void defaultStrategyInitializesSchemaOnAnOlderServer() {
		var model = mock(EmbeddingModel.class);
		var legacy = Neo4jVectorStore.builder(driver, model)
			.embeddingDimension(3)
			.label("LegacySchemaDocument")
			.indexName("legacy-schema-index")
			.constraintName("legacy-schema-constraint")
			.initializeSchema(true)
			.build();
		legacy.afterPropertiesSet();
		try (var session = driver.session()) {
			assertThat(session
				.run("SHOW VECTOR INDEXES YIELD name WHERE name = 'legacy-schema-index' RETURN count(*) AS count")
				.single()
				.get("count")
				.asInt()).isEqualTo(1);
		}
	}

}
