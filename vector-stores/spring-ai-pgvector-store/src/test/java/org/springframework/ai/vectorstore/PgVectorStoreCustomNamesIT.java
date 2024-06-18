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
import org.junit.jupiter.api.Test;
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
		.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private final ApplicationContextRunner customContextRunner = new ApplicationContextRunner()

		.withPropertyValues("test.spring.ai.vectorstore.pgvector.distanceType=COSINE_DISTANCE",

				// JdbcTemplate configuration
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	@Test
	public void testShouldSucceedWhenCustomTableExists() {

		String tableName = "customvectortable";

		customContextRunner.withUserConfiguration(TestApplication.class)
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.pgvector.createTables=true")
			.run(context -> {

				assertThat(context).hasNotFailed();
				dropTableByName(context, tableName);

			});

	}

	@Test
	public void testShouldFailWhenCustomTableDoesNotExist() {

		String tableName = "customvectortable";

		contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName)

			.run(context -> {

				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalStateException.class)
					.hasMessageContaining(tableName + " does not exist");

			});

	}

	@Test
	public void testShouldFailOnSQLInjectionInTableName() {

		String tableName = "users; DROP TABLE users;";

		contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName)

			.run(context -> {

				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Table name should only contain alphanumeric characters and underscores");

			});

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.vectorTableName}")
		String vectorTableName;

		@Value("${test.spring.ai.vectorstore.pgvector.createTables:false}")
		Boolean createTables;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			return new PgVectorStore(vectorTableName, jdbcTemplate, embeddingModel,
					PgVectorStore.INVALID_EMBEDDING_DIMENSION, PgVectorStore.PgDistanceType.COSINE_DISTANCE, true,
					PgIndexType.HNSW, true);
		}

		private void createCustomTable(JdbcTemplate jdbcTemplate) {
			int dimensions = 768;

			if (null == vectorTableName || vectorTableName.isEmpty()) {
				System.out.println("Table name is not provided");
				return;
			}
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
			System.out.println("Table created: " + vectorTableName);
			String vectorIndexName = vectorTableName + "_index";

			PgVectorStore.PgDistanceType distanceType = PgVectorStore.PgDistanceType.COSINE_DISTANCE;

			PgIndexType createIndexMethod = PgIndexType.HNSW;

			jdbcTemplate.execute(String.format("""
					CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)
					""", vectorIndexName, vectorTableName, createIndexMethod, distanceType.index));
			System.out.println("Index created: " + vectorTableName);
			System.out.println("Completed: " + vectorTableName);
		}

		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			if (createTables)
				createCustomTable(jdbcTemplate);
			return jdbcTemplate;
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
