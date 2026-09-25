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

package org.springframework.ai.vectorstore.neo4j.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Testcontainers
class Neo4jVectorStoreSearchAutoConfigurationIT {

	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:2026.01").withRandomPassword();

	private static Driver driver;

	private static EmbeddingModel embeddingModel;

	@BeforeAll
	static void setUp() {
		driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(),
				AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
		embeddingModel = mock(EmbeddingModel.class);
		given(embeddingModel.dimensions()).willReturn(3);
		given(embeddingModel.embed("query")).willReturn(new float[] { 1.0f, 0.0f, 0.0f });
		try (var session = driver.session()) {
			session
				.run("""
						CREATE (:AutoSearchDocument {id: 'frontend', text: 'frontend', `metadata.category`: 'frontend', embedding: [1.0,0.0,0.0]}),
						(:AutoSearchDocument {id: 'backend', text: 'backend', `metadata.category`: 'backend', embedding: [0.8,0.6,0.0]})
						""")
				.consume();
		}
	}

	@AfterAll
	static void tearDown() {
		if (driver != null) {
			driver.close();
		}
	}

	private ApplicationContextRunner contextRunner() {
		return new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(Neo4jVectorStoreAutoConfiguration.class))
			.withBean(Driver.class,
					() -> GraphDatabase.driver(neo4jContainer.getBoltUrl(),
							AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword())))
			.withBean(EmbeddingModel.class, () -> embeddingModel)
			.withPropertyValues("spring.ai.vectorstore.neo4j.label=AutoSearchDocument",
					"spring.ai.vectorstore.neo4j.embedding-dimension=3",
					"spring.ai.vectorstore.neo4j.constraint-name=auto-search-constraint",
					"spring.ai.vectorstore.neo4j.initialize-schema=true");
	}

	@Test
	void bindsSearchStrategyAndFilterFieldsThroughToRealSearch() {
		contextRunner()
			.withPropertyValues("spring.ai.vectorstore.neo4j.index-name=auto-search-index",
					"spring.ai.vectorstore.neo4j.search-strategy=search",
					"spring.ai.vectorstore.neo4j.filterable-metadata-fields[0]=category")
			.run(context -> {
				assertThat(context).hasNotFailed();
				var properties = context.getBean(Neo4jVectorStoreProperties.class);
				assertThat(properties.getSearchStrategy()).isEqualTo(Neo4jVectorStore.SearchStrategy.SEARCH);
				assertThat(properties.getFilterableMetadataFields()).isEqualTo(List.of("category"));
				var results = context.getBean(Neo4jVectorStore.class)
					.similaritySearch(SearchRequest.builder()
						.query("query")
						.topK(1)
						.filterExpression("category == 'backend'")
						.build());
				assertThat(results).singleElement()
					.satisfies(document -> assertThat(document.getId()).isEqualTo("backend"));
			});
	}

	@Test
	void defaultsToTheExistingProcedureStrategy() {
		contextRunner().withPropertyValues("spring.ai.vectorstore.neo4j.index-name=auto-legacy-index").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(Neo4jVectorStoreProperties.class).getSearchStrategy())
				.isEqualTo(Neo4jVectorStore.SearchStrategy.VECTOR_QUERY);
			assertThat(context.getBean(Neo4jVectorStore.class)
				.similaritySearch(SearchRequest.builder()
					.query("query")
					.topK(1)
					.filterExpression("category == 'backend'")
					.build()))
				.isEmpty();
		});
	}

	@Test
	void rejectsFilterFieldsWithoutSearchStrategy() {
		contextRunner().withPropertyValues("spring.ai.vectorstore.neo4j.filterable-metadata-fields[0]=category")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasStackTraceContaining("requires the SEARCH strategy");
			});
	}

}
