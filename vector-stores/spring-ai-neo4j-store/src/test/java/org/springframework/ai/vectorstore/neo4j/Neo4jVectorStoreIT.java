/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.ai.document.DocumentMetadata;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gerrit Meier
 * @author Michael Simons
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class Neo4jVectorStoreIT {

	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(Neo4jImage.DEFAULT_IMAGE).withRandomPassword();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner
			.run(context -> context.getBean(Driver.class).executableQuery("MATCH (n) DETACH DELETE n").execute());
	}

	@Test
	void addAndSearchTest() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());
			assertThat(results2).isEmpty();
		});
	}

	@Test
	void searchWithFilters() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020, "foo bar 1", "bar.foo"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			SearchRequest searchRequest = SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.build();

			List<Document> results = vectorStore.similaritySearch(searchRequest);

			assertThat(results).hasSize(3);

			results = vectorStore
				.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == 'NL'").build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore
				.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country in ['NL']").build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore
				.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country nin ['BG']").build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore
				.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country not in ['BG']").build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore
				.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == 'BG'").build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(
					SearchRequest.from(searchRequest).filterExpression("country == 'BG' && year == 2020").build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(searchRequest)
				.filterExpression("(country == 'BG' && year == 2020) || (country == 'NL')")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), nlDocument.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(searchRequest)
				.filterExpression("NOT((country == 'BG' && year == 2020) || (country == 'NL'))")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("\"foo bar 1\" == 'bar.foo'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			try {
				vectorStore
					.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == NL").build());
				Assert.fail("Invalid filter expression should have been cached!");
			}
			catch (FilterExpressionTextParser.FilterExpressionParseException e) {
				assertThat(e.getMessage()).contains("Line: 1:17, Error: no viable alternative at input 'NL'");
			}
		});
	}

	@Test
	void documentUpdateTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

		});
	}

	@Test
	void searchThresholdTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great").topK(5).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
		});
	}

	@Test
	void ensureVectorIndexGetsCreated() {
		this.contextRunner.run(context -> assertThat(context.getBean(Driver.class)
			.executableQuery(
					"SHOW indexes yield name, type WHERE name = 'spring-ai-document-index' AND type = 'VECTOR' return count(*) > 0")
			.execute()
			.records()
			.get(0) // get first record
			.get(0)
			.asBoolean()) // get returned result
			.isTrue());
	}

	@Test
	void ensureIdIndexGetsCreated() {
		this.contextRunner.run(context -> assertThat(context.getBean(Driver.class)
			.executableQuery(
					"SHOW indexes yield labelsOrTypes, properties, type WHERE any(x in labelsOrTypes where x = 'Document')  AND any(x in properties where x = 'id') AND type = 'RANGE' return count(*) > 0")
			.execute()
			.records()
			.get(0) // get first record
			.get(0)
			.asBoolean()) // get returned result
			.isTrue());
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Driver driver, EmbeddingModel embeddingModel) {

			return Neo4jVectorStore.builder(driver, embeddingModel).initializeSchema(true).build();
		}

		@Bean
		public Driver driver() {
			return GraphDatabase.driver(neo4jContainer.getBoltUrl(),
					AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}