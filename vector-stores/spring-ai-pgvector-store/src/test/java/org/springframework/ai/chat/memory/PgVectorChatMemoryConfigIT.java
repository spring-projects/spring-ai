/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.memory;

import java.util.List;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.vectorstore.PgVectorImage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class PgVectorChatMemoryConfigIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PgVectorChatMemoryIT.TestApplication.class)
		.withPropertyValues(
				// JdbcTemplate configuration
				String.format("app.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", postgresContainer.getUsername()),
				String.format("app.datasource.password=%s", postgresContainer.getPassword()),
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	static String schemaName = "config_test";
	static String tableName = "ai_chat_config_test";
	static String sessionIdColumnName = "id_config_test";
	static String exchangeIdColumnName = "timestamp_config_test";
	static String assistantColumnName = "assistant_config_test";
	static String userColumnName = "\"user_config_test\"";

	@Test
	void initializeSchema_withValueTrue_shouldCreateSchema() {
		this.contextRunner.run(context -> {
			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var config = PgVectorChatMemoryConfig.builder()
				.withInitializeSchema(true)
				.withSchemaName(schemaName)
				.withTableName(tableName)
				.withSessionIdColumnName(sessionIdColumnName)
				.withExchangeIdColumnName(exchangeIdColumnName)
				.withAssistantColumnName(assistantColumnName)
				.withUserColumnName(userColumnName)
				.withJdbcTemplate(jdbcTemplate)
				.build();
			config.initializeSchema();

			var expectedColumns = List.of(sessionIdColumnName, exchangeIdColumnName, assistantColumnName,
					userColumnName.replace("\"", ""));

			// Verify that the schema, table, and index was created
			var hasSchema = jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)", boolean.class,
					schemaName);
			var hasTable = jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)",
					boolean.class, schemaName, tableName);
			var tableColumns = jdbcTemplate.queryForList(
					"SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
					String.class, schemaName, tableName);
			var indexName = jdbcTemplate.queryForObject(
					"SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?", String.class, schemaName,
					tableName);

			assertThat(hasSchema).isTrue();
			assertThat(hasTable).isTrue();
			assertThat(expectedColumns.containsAll(tableColumns)).isTrue();
			assertThat(String.format("%s_%s_%s_idx", tableName, sessionIdColumnName, exchangeIdColumnName))
					.isEqualTo(indexName);

			// Cleanup for the other tests
			jdbcTemplate.update(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName));
		});
	}

	@Test
	void initializeSchema_withValueFalse_shouldNotCreateSchema() {
		this.contextRunner.run(context -> {
			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var config = PgVectorChatMemoryConfig.builder()
				.withInitializeSchema(false)
				.withSchemaName(schemaName)
				.withTableName(tableName)
				.withSessionIdColumnName(sessionIdColumnName)
				.withExchangeIdColumnName(exchangeIdColumnName)
				.withAssistantColumnName(assistantColumnName)
				.withUserColumnName(userColumnName)
				.withJdbcTemplate(jdbcTemplate)
				.build();
			config.initializeSchema();

			// Verify that the schema, table, and index was created
			var hasSchema = jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)", boolean.class,
					schemaName);
			var hasTable = jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)",
					boolean.class, schemaName, tableName);
			var columnCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
					Integer.class, schemaName, tableName);
			var hasIndex = jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = ? AND tablename = ?)", Boolean.class,
					schemaName, tableName);

			assertThat(hasSchema).isFalse();
			assertThat(hasTable).isFalse();
			assertThat(columnCount).isZero();
			assertThat(hasIndex).isFalse();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
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

	}

}
