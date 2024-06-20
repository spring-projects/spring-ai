/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.pgvector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Muthukumaran Navaneethakrishnan
 */
@Testcontainers
public class PgVectorStoreAutoConfigurationCustomNamesIT {

	@Container
	static GenericContainer<?> postgresContainer = new GenericContainer<>("pgvector/pgvector:0.7.2-pg16")
		.withEnv("POSTGRES_USER", "postgres")
		.withEnv("POSTGRES_PASSWORD", "postgres")
		.withExposedPorts(5432);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PgVectorStoreAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",
				// JdbcTemplate configuration
				String.format("spring.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"spring.datasource.username=postgres", "spring.datasource.password=postgres");

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean isTableExists(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '" + tableName + "')",
				Boolean.class);
	}

	private static boolean isSchemaExists(ApplicationContext context, String schemaName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT FROM information_schema.schemata WHERE schema_name = ?)";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName);
	}

	private static boolean isFullyQualifiedTableExists(ApplicationContext context, String schemaName,
			String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
	}

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	private static int getRowCount(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

		// Assuming tableName is safe and validated against SQL injection
		String sql = "SELECT COUNT(*) FROM " + tableName;

		// Execute the SQL query to count rows in the table
		return jdbcTemplate.queryForObject(sql, Integer.class);
	}

	@Test
	public void shouldCreateDefaultTableWhenNoTableNameSpecified() {

		String expectedTable = PgVectorStore.DEFAULT_VECTOR_TABLE_NAME;

		List<String> items = new ArrayList<>();

		contextRunner.withPropertyValues(items.toArray(new String[0])).run(context -> {

			boolean tableExists = isTableExists(context, expectedTable);
			assertThat(tableExists).withFailMessage("Expected table '" + expectedTable + "' to exist.").isTrue();
			dropTableByName(context, expectedTable);
		});
	}

	// should not override existing table , if table already exists
	@Test
	public void shouldPreserveExistingTable() {

		List<String> items = new ArrayList<>();
		String tableName = "customvectortable";
		int existingRowCount = 5;
		items.add("spring.ai.vectorstore.pgvector.vectorTableName=" + tableName);

		items.add("test.spring.ai.vectorstore.pgvector.createTables=true");
		items.add("test.spring.ai.vectorstore.pgvector.insertCount=" + existingRowCount);
		contextRunner.withPropertyValues(items.toArray(new String[0]))

			.run(context -> {
				assertThat(context).hasNotFailed();
				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(documents);
				int count = getRowCount(context, tableName);
				assertThat(count).isEqualTo(documents.size() + existingRowCount);

			});
	}

	@Test
	public void shouldInsertDocumentsIntoSpecifiedTable() {

		List<String> items = new ArrayList<>();
		String tableName = "customvectortable";
		items.add("spring.ai.vectorstore.pgvector.vectorTableName=" + tableName);
		items.add("test.spring.ai.vectorstore.pgvector.createTables=true");

		contextRunner.withPropertyValues(items.toArray(new String[0]))

			.run(context -> {

				assertThat(context).hasNotFailed();
				VectorStore vectorStore = context.getBean(VectorStore.class);

				vectorStore.add(documents);
				int count = getRowCount(context, tableName);
				assertThat(count).isEqualTo(documents.size());

			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Value("${spring.ai.vectorstore.pgvector.vectorTableName:#{null}}")
		String vectorTableName;

		@Value("${spring.ai.vectorstore.pgvector.schemaName:public}")
		String schemaName;

		@Value("${test.spring.ai.vectorstore.pgvector.createTables:false}")
		Boolean createTables;

		@Value("${test.spring.ai.vectorstore.pgvector.insertCount:0}")
		Integer insertCount;

		@Autowired
		JdbcTemplate jdbcTemplate;

		int dimensions = 384;

		@PostConstruct
		public void init() {
			if (createTables) {
				createCustomTable(jdbcTemplate);
				for (int i = 0; i < insertCount; i++) {
					insertRandomData(jdbcTemplate);
				}
			}
		}

		public void insertRandomData(JdbcTemplate jdbcTemplate) {
			// Generate UUID
			UUID id = UUID.randomUUID();

			// Generate random content
			String content = "RandomContent_" + UUID.randomUUID().toString().substring(0, 8); // Short
			// random
			// string

			// Generate random array of floats
			Random random = new Random();
			StringBuilder embeddingBuilder = new StringBuilder("[");
			for (int i = 0; i < dimensions; i++) {
				if (i > 0) {
					embeddingBuilder.append(",");
				}
				float randomFloat = -1.0f + 2.0f * random.nextFloat(); // Generates a
				// float between
				// -1.0 and 1.0
				embeddingBuilder.append(String.format("%.9f", randomFloat));
			}
			embeddingBuilder.append("]");

			String sql = String.format(
					"INSERT INTO %s.%s (id, content, metadata, embedding) " + "VALUES ('%s', '%s', '{}', '%s')",
					schemaName, vectorTableName, id, content, embeddingBuilder.toString());

			int rowsAffected = jdbcTemplate.update(sql);
			System.out.println("Inserted " + rowsAffected + " row(s).");
		}

		private void createCustomTable(JdbcTemplate jdbcTemplate) {

			jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
			jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
			jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

			jdbcTemplate.execute("DROP TABLE IF EXISTS " + vectorTableName);

			jdbcTemplate.execute(String.format("""
					CREATE TABLE IF NOT EXISTS %s (
						id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
						content text,
						metadata json,
						embedding vector(%d)
					)
					""", vectorTableName, dimensions));
			// System.out.println("Table created: " + vectorTableName);
			String vectorIndexName = vectorTableName + "_index";

			PgVectorStore.PgDistanceType distanceType = PgVectorStore.PgDistanceType.COSINE_DISTANCE;

			PgVectorStore.PgIndexType createIndexMethod = PgVectorStore.PgIndexType.HNSW;

			jdbcTemplate.execute(String.format("""
					CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)
					""", vectorIndexName, vectorTableName, createIndexMethod, distanceType.index));

		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
