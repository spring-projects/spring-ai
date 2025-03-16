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

package org.springframework.ai.vectorstore.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.Assert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
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

@Testcontainers
@Disabled("Oracle image is 2GB")
public class OracleVectorStoreIT extends BaseVectorStoreTests {

	@Container
	static OracleContainer oracle23aiContainer = new OracleContainer(OracleImage.DEFAULT_IMAGE).withCopyFileToContainer(
			MountableFile.forClasspathResource("/initialize.sql"), "/container-entrypoint-initdb.d/initialize.sql");

	final List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestClient.class)
		.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=COSINE",
				"test.spring.ai.vectorstore.oracle.dimensions=384",
				// JdbcTemplate configuration
				String.format("app.datasource.url=%s", oracle23aiContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", oracle23aiContainer.getUsername()),
				String.format("app.datasource.password=%s", oracle23aiContainer.getPassword()),
				"app.datasource.type=oracle.jdbc.pool.OracleDataSource");

	public static String getText(final String uri) {
		try {
			return new DefaultResourceLoader().getResource(uri).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void dropTable(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName + " PURGE");
	}

	private static boolean isSortedBySimilarity(final List<Document> documents) {
		final List<Double> scores = documents.stream().map(Document::getScore).toList();

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
		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=COSINE",
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				testFunction.accept(vectorStore);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "DOT", "EUCLIDEAN", "EUCLIDEAN_SQUARED", "MANHATTAN" })
	public void addAndSearch(String distanceType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues(
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
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

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@ParameterizedTest(name = "Distance {0}, search accuracy {1} : {displayName} ")
	@CsvSource({ "COSINE,-1", "DOT,-1", "EUCLIDEAN,-1", "EUCLIDEAN_SQUARED,-1", "MANHATTAN,-1", "COSINE,75", "DOT,80",
			"EUCLIDEAN,60", "EUCLIDEAN_SQUARED,30", "MANHATTAN,42" })
	public void searchWithFilters(String distanceType, int searchAccuracy) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.oracle.searchAccuracy=" + searchAccuracy)
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
				catch (FilterExpressionTextParser.FilterExpressionParseException e) {
					assertThat(e.getMessage()).contains("Line: 1:17, Error: no viable alternative at input 'NL'");
				}

				// Remove all documents from the store
				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "DOT", "EUCLIDEAN", "EUCLIDEAN_SQUARED", "MANHATTAN" })
	public void documentUpdate(String distanceType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues(
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
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

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "DOT" })
	public void searchWithThreshold(String distanceType) {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues(
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore.similaritySearch(
						SearchRequest.builder().query("Time Shelter").topK(5).similarityThresholdAll().build());

				assertThat(fullResult).hasSize(3);

				assertThat(isSortedBySimilarity(fullResult)).isTrue();

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2d;

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Time Shelter")
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(1).getId());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@Test
	void deleteWithComplexFilterExpression() {
		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=COSINE",
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				var doc1 = new Document("Content 1", Map.of("type", "A", "priority", 1));
				var doc2 = new Document("Content 2", Map.of("type", "A", "priority", 2));
				var doc3 = new Document("Content 3", Map.of("type", "B", "priority", 1));

				vectorStore.add(List.of(doc1, doc2, doc3));

				// Complex filter expression: (type == 'A' AND priority > 1)
				Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
						new Filter.Key("priority"), new Filter.Value(1));
				Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
						new Filter.Value("A"));
				Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
						priorityFilter);

				vectorStore.delete(complexFilter);

				var results = vectorStore.similaritySearch(
						SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

				assertThat(results).hasSize(2);
				assertThat(results.stream()
					.map(doc -> doc.getMetadata().get("type").toString().replace("\"", ""))
					.collect(Collectors.toList())).containsExactlyInAnyOrder("A", "B");
				assertThat(results.stream()
					.map(doc -> Integer.parseInt(doc.getMetadata().get("priority").toString()))
					.collect(Collectors.toList())).containsExactlyInAnyOrder(1, 1);

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=COSINE",
					"test.spring.ai.vectorstore.oracle.searchAccuracy=" + OracleVectorStore.DEFAULT_SEARCH_ACCURACY)
			.run(context -> {
				OracleVectorStore vectorStore = context.getBean(OracleVectorStore.class);
				Optional<JdbcTemplate> nativeClient = vectorStore.getNativeClient();
				assertThat(nativeClient).isPresent();
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestClient {

		@Value("${test.spring.ai.vectorstore.oracle.distanceType}")
		OracleVectorStore.OracleVectorStoreDistanceType distanceType;

		@Value("${test.spring.ai.vectorstore.oracle.searchAccuracy}")
		int searchAccuracy;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return OracleVectorStore.builder(jdbcTemplate, embeddingModel)
				.tableName(OracleVectorStore.DEFAULT_TABLE_NAME)
				.indexType(OracleVectorStore.OracleVectorStoreIndexType.IVF)
				.distanceType(this.distanceType)
				.dimensions(384)
				.searchAccuracy(this.searchAccuracy)
				.initializeSchema(true)
				.removeExistingVectorStoreTable(true)
				.forcedNormalization(true)
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
		public OracleDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(OracleDataSource.class).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			try {
				TransformersEmbeddingModel tem = new TransformersEmbeddingModel();
				tem.afterPropertiesSet();
				return tem;
			}
			catch (Exception e) {
				throw new RuntimeException("Failed initializing embedding model", e);
			}
		}

	}

}
