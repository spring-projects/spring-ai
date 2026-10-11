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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ClientException;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Testcontainers
class Neo4jVectorStoreSearchIT {

	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:2026.01").withRandomPassword();

	private static final List<String> FILTER_FIELDS = List.of("category", "year", "active");

	private static Driver driver;

	private static EmbeddingModel embeddingModel;

	private static Neo4jVectorStore store;

	@BeforeAll
	static void setUp() {
		driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(),
				AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
		embeddingModel = mock(EmbeddingModel.class);
		given(embeddingModel.dimensions()).willReturn(3);
		given(embeddingModel.embed("query")).willReturn(new float[] { 1.0f, 0.0f, 0.0f });
		given(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
			.willAnswer(invocation -> {
				List<Document> documents = invocation.getArgument(0);
				return documents.stream()
					.map(document -> new float[] { 1.0f, ((Number) document.getMetadata().get("offset")).floatValue(),
							0.0f })
					.toList();
			});
		store = searchBuilder().initializeSchema(true).build();
		store.afterPropertiesSet();
		var documents = new ArrayList<Document>();
		for (int i = 1; i <= 5; i++) {
			documents.add(Document.builder()
				.id("f" + i)
				.text("frontend")
				.metadata(Map.of("category", "frontend", "offset", i / 100.0, "year", 2000 + i, "active", true))
				.build());
		}
		for (int i = 0; i < 10; i++) {
			documents.add(Document.builder()
				.id("b" + i)
				.text("backend")
				.metadata(Map.of("category", "backend", "offset", 0.5 + i / 100.0, "year", 2000 + i, "active",
						i % 2 == 0))
				.build());
		}
		store.add(documents);
		var legacy = legacyStore(true);
		legacy.afterPropertiesSet();
	}

	private static Neo4jVectorStore.Builder searchBuilder() {
		return Neo4jVectorStore.builder(driver, embeddingModel)
			.label("SearchDocument")
			.indexName("search-document-index")
			.constraintName("search-document-constraint")
			.embeddingDimension(3)
			.searchStrategy(Neo4jVectorStore.SearchStrategy.SEARCH)
			.filterableMetadataFields(FILTER_FIELDS);
	}

	private static Neo4jVectorStore legacyStore(boolean initializeSchema) {
		return Neo4jVectorStore.builder(driver, embeddingModel)
			.label("SearchDocument")
			.indexName("legacy-search-index")
			.constraintName("search-document-constraint")
			.embeddingDimension(3)
			.initializeSchema(initializeSchema)
			.build();
	}

	@AfterAll
	static void tearDown() {
		if (driver != null) {
			driver.close();
		}
	}

	@Test
	void filtersInsideTheIndexInsteadOfFilteringGlobalCandidates() {
		var request = SearchRequest.builder()
			.query("query")
			.topK(5)
			.similarityThreshold(0.0)
			.filterExpression("category == 'backend'")
			.build();
		assertThat(legacyStore(false).similaritySearch(request)).isEmpty();
		assertThat(store.similaritySearch(request)).hasSize(5).allSatisfy(document -> {
			assertThat(document.getMetadata()).containsEntry("category", "backend");
			assertThat(document.getText()).isEqualTo("backend");
			assertThat(document.getScore()).isNotNull().isBetween(0.9, 1.0);
		});
	}

	@Test
	void supportsUnfilteredSearchAndTopK() {
		assertThat(store.similaritySearch(SearchRequest.builder().query("query").topK(3).build())).hasSize(3)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("category", "frontend"));
	}

	@Test
	void appliesThresholdAfterFilteredSearch() {
		assertThat(store.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(5)
			.filterExpression("category == 'backend'")
			.similarityThreshold(0.99)
			.build())).isEmpty();
	}

	@Test
	void returnsFewerResultsWhenOnlyOneDocumentMatches() {
		assertThat(store.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(5)
			.filterExpression("category == 'backend' && year == 2005")
			.build())).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "year > 2001", "year >= 2002", "year < 2008", "year <= 2007",
			"(year >= 2002 && year < 2008)", "active == true" })
	void supportsComparisonsAndConjunctions(String predicate) {
		var results = store.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(2)
			.filterExpression("category == 'backend' && " + predicate)
			.build());
		assertThat(results).hasSize(2).allSatisfy(document -> {
			assertThat(document.getMetadata()).containsEntry("category", "backend");
			int year = ((Number) document.getMetadata().get("year")).intValue();
			switch (predicate) {
				case "year > 2001", "year >= 2002" -> assertThat(year).isGreaterThanOrEqualTo(2002);
				case "year < 2008", "year <= 2007" -> assertThat(year).isLessThanOrEqualTo(2007);
				case "(year >= 2002 && year < 2008)" -> assertThat(year).isBetween(2002, 2007);
				default -> assertThat(document.getMetadata()).containsEntry("active", true);
			}
		});
	}

	@Test
	void supportsStringRangePredicates() {
		assertThat(store.similaritySearch(SearchRequest.builder()
			.query("query")
			.topK(2)
			.filterExpression("category >= 'backend' && category < 'frontend'")
			.build())).hasSize(2)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("category", "backend"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "category == 'backend' || year > 2002", "category != 'frontend'",
			"category in ['backend']", "year > 2001 && year >= 2002", "year == 2005 && year > 2001" })
	void rejectsUnsupportedPredicatesWithoutFallingBack(String predicate) {
		assertThatThrownBy(() -> store
			.similaritySearch(SearchRequest.builder().query("query").filterExpression(predicate).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("SEARCH");
	}

	@Test
	void requiresFilterFieldsToBeConfigured() {
		var unconfigured = searchBuilder().filterableMetadataFields(List.of()).build();
		assertThat(unconfigured.similaritySearch(SearchRequest.builder().query("query").topK(2).build())).hasSize(2);
		assertThatThrownBy(() -> unconfigured
			.similaritySearch(SearchRequest.builder().query("query").filterExpression("category == 'backend'").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("filterableMetadataFields");
	}

	@Test
	void reusesACompatibleExistingIndex() {
		var reused = searchBuilder().filterableMetadataFields(List.of("category")).initializeSchema(true).build();
		reused.afterPropertiesSet();
		assertThat(reused.similaritySearch(
				SearchRequest.builder().query("query").topK(5).filterExpression("category == 'backend'").build()))
			.hasSize(5);
	}

	@Test
	void rejectsAnExistingIndexWithoutFilterFieldsDuringInitialization() {
		var incompatible = searchBuilder().indexName("legacy-search-index").initializeSchema(true).build();
		assertThatThrownBy(incompatible::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("incompatible")
			.hasMessageContaining("not migrated");
		try (var session = driver.session()) {
			var properties = session
				.run("SHOW VECTOR INDEXES YIELD name, properties WHERE name = 'legacy-search-index' RETURN properties")
				.single()
				.get("properties")
				.asList(org.neo4j.driver.Value::asString);
			assertThat(properties).containsExactly("embedding");
		}
	}

	@Test
	void surfacesMissingFilterFieldsOnAnExternallyManagedIndex() {
		var external = searchBuilder().indexName("legacy-search-index").build();
		assertThatThrownBy(() -> external
			.similaritySearch(SearchRequest.builder().query("query").filterExpression("category == 'backend'").build()))
			.isInstanceOf(ClientException.class)
			.hasMessageContaining("metadata.category");
	}

	@ParameterizedTest
	@CsvSource({ "4,COSINE", "3,EUCLIDEAN" })
	void rejectsIncompatibleExistingIndexConfiguration(int dimensions, Neo4jVectorStore.Neo4jDistanceType metric) {
		var incompatible = searchBuilder().embeddingDimension(dimensions)
			.distanceType(metric)
			.initializeSchema(true)
			.build();
		assertThatThrownBy(incompatible::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("incompatible");
	}

	@Test
	void escapesIdentifiersAndBindsFilterValues() {
		String value = "value' OR true //";
		var unusual = Neo4jVectorStore.builder(driver, embeddingModel)
			.label("Search `Document")
			.indexName("search` index")
			.constraintName("unusual-search-constraint")
			.embeddingProperty("vector data")
			.embeddingDimension(3)
			.searchStrategy(Neo4jVectorStore.SearchStrategy.SEARCH)
			.filterableMetadataFields(List.of("odd`key"))
			.initializeSchema(true)
			.build();
		unusual.afterPropertiesSet();
		try (var session = driver.session()) {
			session
				.run("""
						CREATE (:`Search ``Document` {id: 'matching', text: 'quoted', `vector data`: [1.0,0.1,0.0], `metadata.odd``key`: $value}),
						(:`Search ``Document` {id: 'excluded', text: 'other', `vector data`: [1.0,0.0,0.0], `metadata.odd``key`: 'other'})
						""",
						Map.of("value", value))
				.consume();
		}
		var filter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("odd`key"),
				new Filter.Value(value));
		assertThat(unusual
			.similaritySearch(SearchRequest.builder().query("query").topK(1).filterExpression(filter).build()))
			.singleElement()
			.satisfies(document -> {
				assertThat(document.getId()).isEqualTo("matching");
				assertThat(document.getMetadata()).containsEntry("odd`key", value);
			});
	}

}
