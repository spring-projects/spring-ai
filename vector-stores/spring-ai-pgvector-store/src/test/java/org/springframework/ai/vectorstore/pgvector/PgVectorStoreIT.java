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
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jihoon Kim
 * @author YeongMin Song
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PgVectorStoreIT extends BaseVectorStoreTests {

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

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
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
				vectorStore.delete(this.documents.stream().map(Document::getId).toList());

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
	public void testToPgTypeWithTextIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.idType=" + "TEXT")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(List.of(new Document("NOT_UUID", "TEXT", new HashMap<>())));

				dropTable(context);
			});
	}

	@Test
	public void testToPgTypeWithSerialIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.idType=" + "SERIAL")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(List.of(new Document("1", "TEXT", new HashMap<>())));

				dropTable(context);
			});
	}

	@Test
	public void testToPgTypeWithBigSerialIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.idType=" + "BIGSERIAL")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(List.of(new Document("1", "TEXT", new HashMap<>())));

				dropTable(context);
			});
	}

	@Test
	public void testBulkOperationWithUuidIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				List<Document> documents = List.of(
						new Document(new RandomIdGenerator().generateId(), "TEXT", new HashMap<>()),
						new Document(new RandomIdGenerator().generateId(), "TEXT", new HashMap<>()),
						new Document(new RandomIdGenerator().generateId(), "TEXT", new HashMap<>()));
				vectorStore.add(documents);

				List<String> idList = documents.stream().map(Document::getId).toList();
				vectorStore.delete(idList);

				dropTable(context);
			});
	}

	@Test
	public void testBulkOperationWithNonUuidIdType() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=" + "COSINE_DISTANCE")
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.idType=" + "TEXT")
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				List<Document> documents = List.of(new Document("NON_UUID_1", "TEXT", new HashMap<>()),
						new Document("NON_UUID_2", "TEXT", new HashMap<>()),
						new Document("NON_UUID_3", "TEXT", new HashMap<>()));
				vectorStore.add(documents);

				List<String> idList = documents.stream().map(Document::getId).toList();
				vectorStore.delete(idList);

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

				assertThatExceptionOfType(FilterExpressionTextParser.FilterExpressionParseException.class)
					.isThrownBy(() -> vectorStore
						.similaritySearch(SearchRequest.from(searchRequest).filterExpression("country == NL").build()))
					.withMessageContaining("Line: 1:17, Error: no viable alternative at input 'NL'");

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
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			PgVectorStore vectorStore = context.getBean(PgVectorStore.class);
			Optional<JdbcTemplate> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();

			dropTable(context);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
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
				.idType(this.idType)
				.distanceType(this.distanceType)
				.initializeSchema(this.initializeSchema)
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
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
