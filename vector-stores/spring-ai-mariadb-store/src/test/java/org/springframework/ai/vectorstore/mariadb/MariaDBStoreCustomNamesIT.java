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

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
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
 * @author Diego Dupin
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class MariaDBStoreCustomNamesIT {

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
				"app.datasource.url=" + mariadbContainer.getJdbcUrl(), "app.datasource.username=mariadb",
				"app.datasource.password=mariadbpwd", "app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	private static void dropTableByName(ApplicationContext context, String name) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + schemaName + "." + name);
	}

	private static boolean isIndexExists(ApplicationContext context, String schemaName, String tableName,
			String indexName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT * FROM information_schema.statistics WHERE TABLE_SCHEMA=? AND"
				+ " TABLE_NAME=? AND INDEX_NAME=? AND INDEX_TYPE='VECTOR')";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName, indexName);
	}

	@SuppressWarnings("null")
	private static boolean isTableExists(ApplicationContext context, String tableName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		return jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT * FROM information_schema.tables WHERE table_schema= ? AND" + " table_name = ?)",
				Boolean.class, schemaName, tableName);
	}

	private static boolean areColumnsExisting(ApplicationContext context, String tableName, String[] fieldNames) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		for (String field : fieldNames) {
			boolean fieldsExists = jdbcTemplate
				.queryForObject("SELECT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema= ? AND"
						+ " table_name = ? AND column_name = ?)", Boolean.class, schemaName, tableName, field);
			if (!fieldsExists) {
				return false;
			}
		}
		return true;
	}

	private static boolean isSchemaExists(ApplicationContext context, String schemaName) {
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		String sql = "SELECT EXISTS (SELECT * FROM information_schema.schemata WHERE schema_name = ?)";
		return jdbcTemplate.queryForObject(sql, Boolean.class, schemaName);
	}

	@Test
	public void shouldCreateDefaultTableAndIndexIfNotPresentInConfig() {
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.schemaValidation=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(isTableExists(context, "vector_store")).isTrue();
				assertThat(isSchemaExists(context, schemaName)).isTrue();
				dropTableByName(context, "vector_store");
			});
	}

	@Test
	public void shouldCreateTableAndIndexIfNotPresentInDatabase() {
		String tableName = "new_vector_table";
		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.vectorTableName=" + tableName)
			.run(context -> {
				assertThat(isTableExists(context, tableName)).isTrue();
				assertThat(isIndexExists(context, schemaName, tableName, tableName + "_embedding_idx")).isTrue();
				assertThat(isTableExists(context, "vector_store")).isFalse();
				dropTableByName(context, tableName);
			});
	}

	@Test
	public void shouldCreateSpecificTableAndIndexIfNotPresentInDatabase() {
		String tableName = "new_vector_table2";
		String contentFieldName = "content2";
		String embeddingFieldName = "embedding2";
		String idFieldName = "id2";
		String metadataFieldName = "metadata2";

		this.contextRunner.withPropertyValues("test.spring.ai.vectorstore.mariadb.vectorTableName=" + tableName)
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.contentFieldName=" + contentFieldName)
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.embeddingFieldName=" + embeddingFieldName)
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.idFieldName=" + idFieldName)
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.metadataFieldName=" + metadataFieldName)
			.run(context -> {
				assertThat(isTableExists(context, tableName)).isTrue();
				assertThat(isIndexExists(context, schemaName, tableName, tableName + "_embedding_idx")).isTrue();
				assertThat(isTableExists(context, "vector_store")).isFalse();
				assertThat(areColumnsExisting(context, tableName,
						new String[] { contentFieldName, embeddingFieldName, idFieldName, metadataFieldName }))
					.isFalse();
				dropTableByName(context, tableName);
			});
	}

	@Test
	public void shouldFailWhenCustomTableIsAbsentAndValidationEnabled() {

		String tableName = "customvectortable";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.mariadb.schemaValidation=true")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasCauseInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Table 'customvectortable' does not exist in schema 'testdb'");
			});
	}

	@Test
	public void shouldFailOnSQLInjectionAttemptInTableName() {

		String tableName = "users; DROP TABLE users;";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.mariadb.schemaValidation=true")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure().getCause()).hasCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Identifier '`users; DROP TABLE users;`' should only contain alphanumeric"
							+ " characters and underscores");
			});
	}

	@Test
	public void shouldFailOnSQLInjectionAttemptInSchemaName() {

		String schemaName = "public; DROP TABLE users;";
		String tableName = "customvectortable";

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.mariadb.vectorTableName=" + tableName,
					"test.spring.ai.vectorstore.mariadb.schemaName=" + schemaName,
					"test.spring.ai.vectorstore.mariadb.schemaValidation=true")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure().getCause()).hasCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Identifier '`public; DROP TABLE users;`' should only contain alphanumeric"
							+ " characters and underscores");
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.mariadb.vectorTableName:}")
		String vectorTableName;

		@Value("${test.spring.ai.vectorstore.mariadb.schemaName:testdb}")
		String schemaName;

		@Value("${test.spring.ai.vectorstore.mariadb.schemaValidation:false}")
		boolean schemaValidation;

		int dimensions = 1536;

		@Bean
		public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {

			return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
				.schemaName(this.schemaName)
				.vectorTableName(this.vectorTableName)
				.schemaValidation(this.schemaValidation)
				.dimensions(this.dimensions)
				.distanceType(MariaDBVectorStore.MariaDBDistanceType.COSINE)
				.removeExistingVectorStoreTable(true)
				.initializeSchema(true)
				.build();
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
