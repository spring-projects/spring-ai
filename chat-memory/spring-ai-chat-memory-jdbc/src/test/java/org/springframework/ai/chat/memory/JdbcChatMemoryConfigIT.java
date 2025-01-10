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
import java.util.Objects;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class JdbcChatMemoryConfigIT {

	@Nested
	class PostgresChatMemoryConfigIT {

		@Container
		@SuppressWarnings("resource")
		static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("chat_memory_config_test")
			.withUsername("postgres")
			.withPassword("postgres");

		private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestApplication.class)
			.withPropertyValues(
					// JdbcTemplate configuration
					String.format("app.datasource.url=%s", postgresContainer.getJdbcUrl()),
					String.format("app.datasource.username=%s", postgresContainer.getUsername()),
					String.format("app.datasource.password=%s", postgresContainer.getPassword()),
					"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

		@Test
		void initializeSchema_withValueTrue_shouldCreateSchema() {
			this.contextRunner.run(context -> {
				var jdbcTemplate = context.getBean(JdbcTemplate.class);
				var config = JdbcChatMemoryConfig.builder()
					.setInitializeSchema(true)
					.jdbcTemplate(jdbcTemplate)
					.build();
				config.initializeSchema();

				var expectedColumns = List.of("conversation_id", "content", "type", "timestamp");

				// Verify that the table and index are created
				var hasTable = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)", boolean.class,
						"ai_chat_memory");
				var tableColumns = jdbcTemplate.queryForList(
						"SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class,
						"ai_chat_memory");
				var indexName = jdbcTemplate.queryForObject("SELECT indexname FROM pg_indexes WHERE tablename = ?",
						String.class, "ai_chat_memory");

				assertThat(hasTable).isTrue();
				assertThat(expectedColumns.containsAll(tableColumns)).isTrue();
				assertThat(indexName).isEqualTo("ai_chat_memory_conversation_id_timestamp_idx");
			});
		}

		@Test
		void initializeSchema_withValueFalse_shouldNotCreateSchema() {
			this.contextRunner.run(context -> {
				var jdbcTemplate = context.getBean(JdbcTemplate.class);

				// Make sure the schema does not exist in the first place
				ScriptUtils.executeSqlScript(Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection(),
						new ClassPathResource("schema-drop.postgresql.sql", JdbcChatMemoryConfigIT.class));

				var config = JdbcChatMemoryConfig.builder()
					.setInitializeSchema(false)
					.jdbcTemplate(jdbcTemplate)
					.build();
				config.initializeSchema();

				// Verify that the table and index was created
				var hasTable = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)", boolean.class,
						"ai_chat_memory");
				var columnCount = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ?", Integer.class,
						"ai_chat_memory");
				var hasIndex = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = ?)", Boolean.class,
						"ai_chat_memory");

				assertThat(hasTable).isFalse();
				assertThat(columnCount).isZero();
				assertThat(hasIndex).isFalse();
			});
		}

	}

	@Nested
	class MariaChatMemoryConfigIT {

		@Container
		@SuppressWarnings("resource")
		static MariaDBContainer<?> mariaContainer = new MariaDBContainer<>("mariadb:11")
			.withDatabaseName("chat_memory_config_test")
			.withUsername("mysql")
			.withPassword("mysql");

		private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(JdbcChatMemoryConfigIT.TestApplication.class)
			.withPropertyValues(
					// JdbcTemplate configuration
					String.format("app.datasource.url=%s", mariaContainer.getJdbcUrl()),
					String.format("app.datasource.username=%s", mariaContainer.getUsername()),
					String.format("app.datasource.password=%s", mariaContainer.getPassword()),
					"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

		@Test
		void initializeSchema_withValueTrue_shouldCreateSchema() {
			this.contextRunner.run(context -> {
				var jdbcTemplate = context.getBean(JdbcTemplate.class);
				var config = JdbcChatMemoryConfig.builder()
					.setInitializeSchema(true)
					.jdbcTemplate(jdbcTemplate)
					.build();
				config.initializeSchema();

				var expectedColumns = List.of("conversation_id", "content", "type", "timestamp");

				// Verify that the table and index are created
				var hasTable = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)", boolean.class,
						"ai_chat_memory");
				var tableColumns = jdbcTemplate.queryForList(
						"SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class,
						"ai_chat_memory");
				var indexNames = jdbcTemplate.queryForList(
						"SELECT index_name, column_name FROM information_schema.statistics WHERE table_name = ?",
						"ai_chat_memory");

				assertThat(hasTable).isTrue();
				assertThat(expectedColumns.containsAll(tableColumns)).isTrue();
				assertThat(indexNames).hasSize(2);
				assertThat(indexNames.get(0).get("index_name"))
					.isEqualTo("ai_chat_memory_conversation_id_timestamp_idx");
				assertThat(indexNames.get(0).get("column_name")).isEqualTo("conversation_id");
				assertThat(indexNames.get(1).get("index_name"))
					.isEqualTo("ai_chat_memory_conversation_id_timestamp_idx");
				assertThat(indexNames.get(1).get("column_name")).isEqualTo("timestamp");
			});
		}

		@Test
		void initializeSchema_withValueFalse_shouldNotCreateSchema() {
			this.contextRunner.run(context -> {
				var jdbcTemplate = context.getBean(JdbcTemplate.class);

				// Make sure the schema does not exist in the first place
				ScriptUtils.executeSqlScript(Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection(),
						new ClassPathResource("schema-drop.mariadb.sql", JdbcChatMemoryConfigIT.class));

				var config = JdbcChatMemoryConfig.builder()
					.setInitializeSchema(false)
					.jdbcTemplate(jdbcTemplate)
					.build();
				config.initializeSchema();

				// Verify that the table and index was created
				var hasTable = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)", boolean.class,
						"ai_chat_memory");
				var columnCount = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ?", Integer.class,
						"ai_chat_memory");
				var hasIndex = jdbcTemplate.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_name = ?)",
						Boolean.class, "ai_chat_memory");

				assertThat(hasTable).isFalse();
				assertThat(columnCount).isZero();
				assertThat(hasIndex).isFalse();
			});
		}

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
