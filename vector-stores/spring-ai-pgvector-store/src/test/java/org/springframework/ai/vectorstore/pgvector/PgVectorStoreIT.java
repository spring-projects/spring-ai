/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.pgvector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.document.id.RandomIdGenerator;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser.FilterExpressionParseException;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jihoon Kim
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PgVectorStoreIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
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

	private static void initSchema(ApplicationContext context) {
		PgVectorStore vectorStore = context.getBean(PgVectorStore.class);
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		// Enable the PGVector, JSONB and UUID support.
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

		jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", PgVectorStore.DEFAULT_SCHEMA_NAME));

		jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s.%s (
					id text PRIMARY KEY,
					content text,
					metadata json,
					embedding vector(%d)
				)
				""", PgVectorStore.DEFAULT_SCHEMA_NAME, PgVectorStore.DEFAULT_TABLE_NAME,
				vectorStore.embeddingDimensions()));
	}

	private static void dropTable(ApplicationContext context) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
	}

	static Stream<Arguments> provideFilters() {
		return Stream.of(Arguments.of("country in ['BG','NL']", 3), // String Filters In
				Arguments.of("year in [2020]", 1), // Numeric Filters In
				Arguments.of("country not in ['BG']", 1), // String Filter Not In
				Arguments.of("year not in [2020]", 2) // Numeric Filter Not In
		);
	}

	private static boolean isSortedBySimilarity(List<Document> docs) {

		List<Double> scores = docs.stream().map(Document::getScore).toList();

		if (CollectionUtils.isEmpty(scores) || scores.size() == 1) {
			return true;
		}

		Iterator<Double> iter = scores.iterator();
		Double current;
		Double previous = iter.next();
		while (iter.hasNext()) {
			current = iter.next();
			if (previous < current) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE_DISTANCE", "EUCLIDEAN_DISTANCE", "NEGATIVE_INNER_PRODUCT" })
	public void addAndSearch(String distanceType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + distanceType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("What is Great Depression").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				List<Document> results2 = vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build());
				assertThat(results2).hasSize(0);

				dropTable(context);
			});
	}

	@Test
	public void testToPgTypeWithUuidIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(List.of(new Document(new RandomIdGenerator().generateId(), "TEXT", new HashMap<>())));

				dropTable(context);
			});
	}

	@Test
	public void testToPgTypeWithNonUuidIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.initializeSchema=" + false)
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.idType=" + "TEXT")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);
				initSchema(context);

				vectorStore.add(List.of(new Document("NOT_UUID", "TEXT", new HashMap<>())));

				dropTable(context);
			});
	}

	@ParameterizedTest(name = "Filter expression {0} should return {1} records ")
	@MethodSource("provideFilters")
	public void searchWithInFilter(String expression, Integer expectedRecords) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE")
			.run(context -> {

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
					.filterExpression(expression)
					.topK(5)
					.similarityThresholdAll()
					.build();

				List<Document> results = vectorStore.similaritySearch(searchRequest);

				assertThat(results).hasSize(expectedRecords);

				// Remove all documents from the store
				dropTable(context);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE_DISTANCE", "EUCLIDEAN_DISTANCE", "NEGATIVE_INNER_PRODUCT" })
	public void searchWithFilters(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + distanceType)
			.run(context -> {

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
				catch (FilterExpressionParseException e) {
					assertThat(e.getMessage()).contains("Line: 1:17, Error: no viable alternative at input 'NL'");
				}

				// Remove all documents from the store
				dropTable(context);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE_DISTANCE", "EUCLIDEAN_DISTANCE", "NEGATIVE_INNER_PRODUCT" })
	public void documentUpdate(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + distanceType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				vectorStore.add(List.of(document));

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				vectorStore.add(List.of(sameIdDocument));

				results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

				dropTable(context);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE_DISTANCE", "EUCLIDEAN_DISTANCE", "NEGATIVE_INNER_PRODUCT" })
	// @ValueSource(strings = { "COSINE_DISTANCE" })
	public void searchWithThreshold(String distanceType) {

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + distanceType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore.similaritySearch(
						SearchRequest.builder().query("Time Shelter").topK(5).similarityThresholdAll().build());

				assertThat(fullResult).hasSize(3);

				assertThat(isSortedBySimilarity(fullResult)).isTrue();

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Time Shelter")
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(1).getId());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

				dropTable(context);
			});
	}

	@Test
	public void deleteByIds() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE")
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				// Create test documents
				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL", "year", 2021));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				// Add documents to store
				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				// Verify initial state
				SearchRequest searchRequest = SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.build();

				List<Document> results = vectorStore.similaritySearch(searchRequest);
				assertThat(results).hasSize(3);

				// Delete two documents by ID
				vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId()));

				// Verify deletion
				results = vectorStore.similaritySearch(searchRequest);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

				// Remove all documents from the store
				dropTable(context);
			});
	}

	@Test
	public void deleteByFilter() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE")
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				// Create test documents
				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL", "year", 2021));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				// Add documents to store
				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				// Verify initial state
				SearchRequest searchRequest = SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.build();

				List<Document> results = vectorStore.similaritySearch(searchRequest);
				assertThat(results).hasSize(3);

				// Create filter to delete all documents with country=BG
				Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.EQ,
						new Filter.Key("country"), new Filter.Value("BG"));

				// Delete documents using filter
				vectorStore.delete(filterExpression);

				// Verify deletion - should only have NL document remaining
				results = vectorStore.similaritySearch(searchRequest);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");

				// Remove all documents from the store
				dropTable(context);
			});
	}

	@Test
	public void deleteWithStringFilterExpression() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL", "year", 2021));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			var searchRequest = SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build();

			List<Document> results = vectorStore.similaritySearch(searchRequest);
			assertThat(results).hasSize(3);

			vectorStore.delete("country == 'BG'");

			results = vectorStore.similaritySearch(searchRequest);
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");

			vectorStore.delete(List.of(nlDocument.getId()));
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			PgVectorStore vectorStore = context.getBean(PgVectorStore.class);
			Optional<JdbcTemplate> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.distanceType}")
		PgVectorStore.PgDistanceType distanceType;

		@Value("${test.spring.ai.vectorstore.pgvector.initializeSchema:true}")
		boolean initializeSchema;

		@Value("${test.spring.ai.vectorstore.pgvector.idType:UUID}")
		PgIdType idType;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.dimensions(PgVectorStore.INVALID_EMBEDDING_DIMENSION)
				.idType(idType)
				.distanceType(this.distanceType)
				.initializeSchema(initializeSchema)
				.indexType(PgIndexType.HNSW)
				.removeExistingVectorStoreTable(true)
				.build();
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
