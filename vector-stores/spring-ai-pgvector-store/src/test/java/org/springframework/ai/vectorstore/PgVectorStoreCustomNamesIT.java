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
package org.springframework.ai.vectorstore;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.PgVectorStore.PgIndexType;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Muthukumaran Navaneethakrishnan
 */
@Testcontainers
public class PgVectorStoreCustomNamesIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues(

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private static boolean isTableExists(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '" + tableName + "')",
				Boolean.class);
	}

	private static boolean isIndexExists(ApplicationContext context, String indexName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		// Query to check if the specified index exists in the database
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = '" + indexName + "')", Boolean.class);
	}

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	@ParameterizedTest(name = "{0} - Verifies creation of specified table and index names")
	@CsvSource({
			// Passing empty string for both tableName and indexName
			"'','',vector_store,spring_ai_vector_index",
			// Passing valid tableName and empty string for indexName
			"customvectortable_no_index,'',customvectortable_no_index,customvectortable_no_index_index",
			// Passing valid values for both tableName and indexName
			"customvectortable,customvectortableindex,customvectortable,customvectortableindex" })
	public void testCustomTableNameAndIndexCreation(String tableName, String indexName, String expectedTable,
			String expectedIndex) {

		contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.pgvector.vectorIndexName=" + indexName)
			.run(context -> {

				System.out.println(" table name: " + tableName);
				System.out.println(" indexName name: " + tableName);
				boolean tableExists = isTableExists(context, expectedTable);
				boolean indexExists = isIndexExists(context, expectedIndex);

				System.out.println("Table '" + expectedTable + "' exists: " + tableExists);
				System.out.println("Index '" + expectedIndex + "' exists: " + indexExists);

				assertThat(tableExists).withFailMessage("Expected table '" + expectedTable + "' to exist.").isTrue();
				assertThat(indexExists).withFailMessage("Expected index '" + expectedIndex + "' to exist.").isTrue();

				// Clean up by dropping the created table
				dropTableByName(context, expectedTable);
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.vectorTableName}")
		String vectorTableName;

		@Value("${test.spring.ai.vectorstore.pgvector.vectorIndexName}")
		String vectorIndexName;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return new PgVectorStore(vectorTableName, vectorIndexName, jdbcTemplate, embeddingModel,
					PgVectorStore.INVALID_EMBEDDING_DIMENSION, PgVectorStore.PgDistanceType.COSINE_DISTANCE, true,
					PgIndexType.HNSW, true);
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
