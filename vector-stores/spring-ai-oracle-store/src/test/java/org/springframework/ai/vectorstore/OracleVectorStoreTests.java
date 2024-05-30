package org.springframework.ai.vectorstore;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
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
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.OracleVectorStore.DEFAULT_SEARCH_ACCURACY;

@Testcontainers
// @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OracleVectorStoreTests {

	@Container
	static OracleContainer oracle23aiContainer = new OracleContainer("gvenzl/oracle-free:23-slim") {
		protected void runInitScriptIfRequired() {
			try {
				final Map<String, String> envMap = new HashMap<>();
				envMap.put("ORACLE_BASE", "/opt/oracle");
				envMap.put("ORACLE_BASE_CONFIG", "/opt/oracle/product/23ai/dbhomeFree");
				envMap.put("ORACLE_BASE_HOME", "/opt/oracle/product/23ai/dbhomeFree");
				envMap.put("ORACLE_HOME", "/opt/oracle/product/23ai/dbhomeFree");
				envMap.put("ORACLE_SID", "FREE");
				envMap.put("NLS_LANG", ".AL32UTF8");

				final ExecConfig ec = ExecConfig.builder().user("oracle").command(new String[] { "/bin/sh", "-c", """
						/opt/oracle/product/23ai/dbhomeFree/bin/sqlplus -s / as sysdba <<EOF
						       -- Exit on any errors
						       WHENEVER SQLERROR EXIT SQL.SQLCODE

						       -- Configure the size of the Vector Pool to 1 GiB.
						       ALTER SYSTEM SET vector_memory_size=1G SCOPE=SPFILE;

						       SHUTDOWN ABORT;
						       STARTUP;

						       exit;
						EOF""" }).workDir("/opt/oracle").envVars(envMap).build();
				execInContainer(ec);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed configuring Oracle Database container", e);
			}
		}
	}.withInitScript("initialize.sql");

	final List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(final String uri) {
		try {
			return new DefaultResourceLoader().getResource(uri).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestClient.class)
		.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=COSINE_DISTANCE",
				"test.spring.ai.vectorstore.oracle.dimensions=384",
				// JdbcTemplate configuration
				String.format("app.datasource.url=%s", oracle23aiContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", oracle23aiContainer.getUsername()),
				String.format("app.datasource.password=%s", oracle23aiContainer.getPassword()),
				"app.datasource.type=oracle.jdbc.pool.OracleDataSource");

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestClient {

		@Value("${test.spring.ai.vectorstore.oracle.distanceType}")
		OracleVectorStore.OracleAIVectorSearchDistanceType distanceType;

		@Value("${test.spring.ai.vectorstore.oracle.searchAccuracy}")
		int searchAccuracy;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return new OracleVectorStore(jdbcTemplate, embeddingModel, OracleVectorStore.DEFAULT_TABLE_NAME,
					OracleVectorStore.OracleAIVectorSearchIndexType.IVF, distanceType, 384, searchAccuracy, true, true,
					true);
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

	private static void dropTable(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName + " PURGE");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "DOT", "EUCLIDEAN", "EUCLIDEAN_SQUARED", "MANHATTAN" })
	public void addAndSearch(String distanceType) {
		contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.oracle.searchAccuracy=" + DEFAULT_SEARCH_ACCURACY)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(documents);

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", "distance");

				// Remove all documents from the store
				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				List<Document> results2 = vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1));
				assertThat(results2).hasSize(0);

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@ParameterizedTest(name = "Distance {0}, search accuracy {1} : {displayName} ")
	@CsvSource({ "COSINE,-1", "DOT,-1", "EUCLIDEAN,-1", "EUCLIDEAN_SQUARED,-1", "MANHATTAN,-1", "COSINE,75", "DOT,80",
			"EUCLIDEAN,60", "EUCLIDEAN_SQUARED,30", "MANHATTAN,42" })
	public void searchWithFilters(String distanceType, int searchAccuracy) {
		contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
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
		contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.oracle.searchAccuracy=" + DEFAULT_SEARCH_ACCURACY)
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

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE", "DOT" })
	public void searchWithThreshold(String distanceType) {
		contextRunner.withPropertyValues("test.spring.ai.vectorstore.oracle.distanceType=" + distanceType)
			.withPropertyValues("test.spring.ai.vectorstore.oracle.searchAccuracy=" + DEFAULT_SEARCH_ACCURACY)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(documents);

				List<Document> fullResult = vectorStore
					.similaritySearch(SearchRequest.query("Time Shelter").withTopK(5).withSimilarityThresholdAll());

				assertThat(fullResult).hasSize(3);

				assertThat(isSortedByDistance(fullResult)).isTrue();

				List<Double> distances = fullResult.stream()
					.map(doc -> (Double) doc.getMetadata().get("distance"))
					.toList();

				double threshold = (distances.get(0) + distances.get(1)) / 2d;

				List<Document> results = vectorStore.similaritySearch(
						SearchRequest.query("Time Shelter").withTopK(5).withSimilarityThreshold(1d - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(1).getId());

				dropTable(context, ((OracleVectorStore) vectorStore).getTableName());
			});
	}

	private static boolean isSortedByDistance(final List<Document> documents) {
		final List<Double> distances = documents.stream()
			.map(doc -> (Double) doc.getMetadata().get("distance"))
			.toList();

		if (CollectionUtils.isEmpty(distances) || distances.size() == 1) {
			return true;
		}

		Iterator<Double> iter = distances.iterator();
		Double current;
		Double previous = iter.next();
		while (iter.hasNext()) {
			current = iter.next();
			if (previous > current) {
				return false;
			}
			previous = current;
		}
		return true;
	}

}
