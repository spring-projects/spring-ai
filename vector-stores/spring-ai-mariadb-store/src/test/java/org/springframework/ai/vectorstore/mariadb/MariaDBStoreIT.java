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

package org.springframework.ai.vectorstore.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.Assert;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser.FilterExpressionParseException;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author Diego Dupin
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class MariaDBStoreIT {

	private static String schemaName = "testdb";

	@Container
	@SuppressWarnings("resource")
	static MariaDBContainer<?> mariadbContainer = new MariaDBContainer<>(MariaDBImage.DEFAULT_IMAGE)
		.withUsername("mariadb")
		.withPassword("mariadbpwd")
		.withDatabaseName(schemaName);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=COSINE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:mariadb://%s:%d/%s?maxQuerySizeToLog=50000",
						mariadbContainer.getHost(), mariadbContainer.getMappedPort(3306), schemaName),
				"app.datasource.username=mariadb", "app.datasource.password=mariadbpwd",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void dropTable(ApplicationContext context) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
	}

	static Stream<Arguments> provideFilters() {
		return Stream.of(Arguments.of("country in ['BG','NL']", 3), // String Filters In
				Arguments.of("year in [2020]", 1), // Numeric Filters In
				Arguments.of("country not in ['BG']", 1), // String Filter Not In
				Arguments.of("year not in [2020]", 1) // Numeric Filter Not In
		);
	}

	private static boolean isSortedByDistance(List<Document> docs) {

		List<Float> distances = docs.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

		if (CollectionUtils.isEmpty(distances) || distances.size() == 1) {
			return true;
		}

		Iterator<Float> iter = distances.iterator();
		Float current, previous = iter.next();
		while (iter.hasNext()) {
			current = iter.next();
			if (previous > current) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "EUCLIDEAN" })
	public void addAndSearch(String distanceType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=" + distanceType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", "distance");

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				List<Document> results2 = vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1));
				assertThat(results2).hasSize(0);

				dropTable(context);
			});
	}

	@ParameterizedTest(name = "Filter expression {0} should return {1} records ")
	@MethodSource("provideFilters")
	public void searchWithInFilter(String expression, Integer expectedRecords) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=COSINE").run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020, "foo bar 1", "bar.foo"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			SearchRequest searchRequest = SearchRequest.query("The World")
				.withFilterExpression(expression)
				.withTopK(5)
				.withSimilarityThresholdAll();

			List<Document> results = vectorStore.similaritySearch(searchRequest);

			assertThat(results).hasSize(expectedRecords);

			// Remove all documents from the store
			dropTable(context);
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "EUCLIDEAN" })
	public void searchWithFilters(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=" + distanceType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020, "foo bar 1", "bar.foo"));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				SearchRequest searchRequest = SearchRequest.query("The World").withTopK(5).withSimilarityThresholdAll();

				List<Document> results = vectorStore.similaritySearch(searchRequest);

				assertThat(results).hasSize(3);

				results = vectorStore.similaritySearch(searchRequest.withFilterExpression("country == 'NL'"));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = vectorStore.similaritySearch(searchRequest.withFilterExpression("country == 'BG'"));

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = vectorStore
					.similaritySearch(searchRequest.withFilterExpression("country == 'BG' && year == 2020"));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				results = vectorStore.similaritySearch(
						searchRequest.withFilterExpression("(country == 'BG' && year == 2020) || (country == 'NL')"));

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), nlDocument.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), nlDocument.getId());

				results = vectorStore.similaritySearch(searchRequest
					.withFilterExpression("NOT((country == 'BG' && year == 2020) || (country == 'NL'))"));

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

				results = vectorStore.similaritySearch(SearchRequest.query("The World")
					.withTopK(5)
					.withSimilarityThresholdAll()
					.withFilterExpression("\"foo bar 1\" == 'bar.foo'"));
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				try {
					vectorStore.similaritySearch(searchRequest.withFilterExpression("country == NL"));
					Assert.fail("Invalid filter expression should have been cached!");
				}
				catch (FilterExpressionParseException e) {
					assertThat(e.getMessage()).contains("Line: 1:17, Error: no viable alternative at input 'NL'");
				}

				// Remove all documents from the store
				dropTable(context);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "EUCLIDEAN" })
	public void documentUpdate(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=" + distanceType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				vectorStore.add(List.of(document));

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				vectorStore.add(List.of(sameIdDocument));

				results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", "distance");

				dropTable(context);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "EUCLIDEAN" })
	public void searchWithThreshold(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.distanceType=" + distanceType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore
					.similaritySearch(SearchRequest.query("Time Shelter").withTopK(5).withSimilarityThresholdAll());

				assertThat(fullResult).hasSize(3);

				assertThat(isSortedByDistance(fullResult)).isTrue();

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get("distance"))
					.toList();

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = vectorStore.similaritySearch(
						SearchRequest.query("Time Shelter").withTopK(5).withSimilarityThreshold(1 - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(1).getId());

				dropTable(context);
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.mariadb.distanceType}")
		MariaDBVectorStore.MariaDBDistanceType distanceType;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return new MariaDBVectorStore(jdbcTemplate, embeddingModel, MariaDBVectorStore.INVALID_EMBEDDING_DIMENSION,
					this.distanceType, true, true);
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("app.datasource")
		public DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
