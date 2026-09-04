/*
 * Copyright 2023-present the original author or authors.
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

import java.util.function.Consumer;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for {@link MariaDBSchemaValidator}, run against a real MariaDB
 * instance so that the SQL used to inspect {@code INFORMATION_SCHEMA} is verified against
 * actual server behavior rather than a mocked {@link JdbcTemplate}.
 *
 * @author dev-xong
 */
@Testcontainers
class MariaDBSchemaValidatorIT {

	private static final String SCHEMA_NAME = "testdb";

	@Container
	@SuppressWarnings("resource")
	static MariaDBContainer<?> mariadbContainer = new MariaDBContainer<>(MariaDBImage.DEFAULT_IMAGE)
		.withUsername("mariadb")
		.withPassword("mariadbpwd")
		.withDatabaseName(SCHEMA_NAME);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void validatesSuccessfullyWhenIdIsSolePrimaryKey() {
		withSchemaValidator("""
				CREATE TABLE vector_store (
					id VARCHAR(36) NOT NULL PRIMARY KEY,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1024) NOT NULL,
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""", schemaValidator -> assertThatNoException().isThrownBy(() -> schemaValidator
			.validateTableSchema(SCHEMA_NAME, "vector_store", "id", "content", "metadata", "embedding", 1024)));
	}

	@Test
	void validatesSuccessfullyWhenIdIsSoleUniqueColumn() {
		withSchemaValidator("""
				CREATE TABLE vector_store (
					id VARCHAR(36) NOT NULL UNIQUE,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1024) NOT NULL,
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""", schemaValidator -> assertThatNoException().isThrownBy(() -> schemaValidator
			.validateTableSchema(SCHEMA_NAME, "vector_store", "id", "content", "metadata", "embedding", 1024)));
	}

	@Test
	void rejectsWhenIdHasNoUniqueConstraint() {
		withSchemaValidator("""
				CREATE TABLE vector_store (
					id VARCHAR(36) NOT NULL,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1024) NOT NULL,
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""",
				schemaValidator -> assertThatIllegalStateException()
					.isThrownBy(() -> schemaValidator.validateTableSchema(SCHEMA_NAME, "vector_store", "id", "content",
							"metadata", "embedding", 1024))
					.withMessageContaining("sole column"));
	}

	@Test
	void rejectsWhenIdIsPartOfCompositePrimaryKey() {
		withSchemaValidator("""
				CREATE TABLE vector_store (
					id VARCHAR(36) NOT NULL,
					tenant_id VARCHAR(36) NOT NULL,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1024) NOT NULL,
					PRIMARY KEY (id, tenant_id),
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""",
				schemaValidator -> assertThatIllegalStateException()
					.isThrownBy(() -> schemaValidator.validateTableSchema(SCHEMA_NAME, "vector_store", "id", "content",
							"metadata", "embedding", 1024))
					.withMessageContaining("sole column"));
	}

	@Test
	void rejectsWhenIdIsPartOfCompositeUniqueConstraint() {
		withSchemaValidator("""
				CREATE TABLE vector_store (
					id VARCHAR(36) NOT NULL,
					tenant_id VARCHAR(36) NOT NULL,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1024) NOT NULL,
					UNIQUE KEY ux_id_tenant (id, tenant_id),
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""",
				schemaValidator -> assertThatIllegalStateException()
					.isThrownBy(() -> schemaValidator.validateTableSchema(SCHEMA_NAME, "vector_store", "id", "content",
							"metadata", "embedding", 1024))
					.withMessageContaining("sole column"));
	}

	private void withSchemaValidator(String createTableSql, Consumer<MariaDBSchemaValidator> assertions) {
		this.contextRunner.run(context -> {
			JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
			jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
			jdbcTemplate.execute(createTableSql);
			assertions.accept(new MariaDBSchemaValidator(jdbcTemplate));
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	public static class TestApplication {

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public DataSourceProperties dataSourceProperties() {
			DataSourceProperties properties = new DataSourceProperties();
			properties.setUrl(mariadbContainer.getJdbcUrl());
			properties.setUsername(mariadbContainer.getUsername());
			properties.setPassword(mariadbContainer.getPassword());
			return properties;
		}

		@Bean
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

	}

}
