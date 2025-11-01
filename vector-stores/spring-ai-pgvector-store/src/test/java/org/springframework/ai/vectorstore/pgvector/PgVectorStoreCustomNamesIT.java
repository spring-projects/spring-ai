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

package org.springframework.ai.vectorstore.pgvector;

import java.util.Random;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Muthukumaran Navaneethakrishnan
 * @author Thomas Vitale
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PgVectorStoreCustomNamesIT {

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

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
	}

	private static boolean isIndexExists(ApplicationContext context, String schemaName, String tableName,
			String indexName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = ? AND tablename = ? AND indexname = ?)";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName, indexName);
	}

	@SuppressWarnings("null")
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

	@Test
	public void shouldCreateDefaultTableAndIndexIfNotPresentInConfig() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.schemaValidation=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(isTableExists(context, "vector_store")).isTrue();
				assertThat(isSchemaExists(context, "public")).isTrue();
				dropTableByName(context, "vector_store");

			});
	}

	@Test
	public void shouldCreateTableAndIndexIfNotPresentInDatabase() {
		String tableName = "new_vector_table";
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName)
			.run(context -> {
				assertThat(isTableExists(context, tableName)).isTrue();
				assertThat(isIndexExists(context, "public", tableName, tableName + "_index")).isTrue();
				assertThat(isTableExists(context, "vector_store")).isFalse();
				dropTableByName(context, tableName);
			});
	}

	@Test
	public void shouldFailWhenCustomTableIsAbsentAndValidationEnabled() {

		String tableName = "customvectortable";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.pgvector.schemaValidation=true")

			.run(context -> {

				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalStateException.class)
					.hasMessageContaining(tableName + " does not exist");

			});
	}

	@Test
	public void shouldFailOnSQLInjectionAttemptInTableName() {

		String tableName = "users; DROP TABLE users;";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.pgvector.schemaValidation=true")

			.run(context -> {

				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Table name should only contain alphanumeric characters and underscores");

			});

	}

	@Test
	public void shouldFailOnSQLInjectionAttemptInSchemaName() {

		String schemaName = "public; DROP TABLE users;";
		String tableName = "customvectortable";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.pgvector.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.pgvector.schemaName=" + schemaName,
					"test.spring.ai.vectorstore.pgvector.schemaValidation=true")

			.run(context -> {

				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Schema name should only contain alphanumeric characters and underscores");

			});

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.pgvector.vectorTableName:}")
		String vectorTableName;

		@Value("${test.spring.ai.vectorstore.pgvector.schemaName:public}")
		String schemaName;

		@Value("${test.spring.ai.vectorstore.pgvector.schemaValidation:false}")
		boolean schemaValidation;

		int dimensions = 768;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {

			return PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.schemaName(this.schemaName)
				.vectorTableName(this.vectorTableName)
				.vectorTableValidationsEnabled(this.schemaValidation)
				.dimensions(this.dimensions)
				.distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
				.removeExistingVectorStoreTable(true)
				.indexType(PgIndexType.HNSW)
				.initializeSchema(true)
				.build();
		}

		public Float[] generateFloatArray(int size, float min, float max) {
			float[] result = new float[size];
			Random random = new Random();

			for (int i = 0; i < size; i++) {
				result[i] = min + random.nextFloat() * (max - min);
			}
			Float[] embeddingObjects = new Float[result.length];
			for (int i = 0; i < result.length; i++) {
				embeddingObjects[i] = result[i]; // Auto-boxing float to Float
			}

			return embeddingObjects;
		}

		//
		@Bean
		public JdbcTemplate myJdbcTemplate(DataSource dataSource) {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

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
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
