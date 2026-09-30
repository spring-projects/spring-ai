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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * @author Junhyeok Park
 */
@Testcontainers
@JdbcTest
public class MariaDBSchemaValidatorIT {

	@ServiceConnection
	@Container
	@SuppressWarnings("resource")
	static MariaDBContainer<?> mariadbContainer = new MariaDBContainer<>(MariaDBImage.DEFAULT_IMAGE)
		.withUsername("mariadb")
		.withPassword("mariadbpwd")
		.withDatabaseName("testdb");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MariaDBSchemaValidator schemaValidator;

	@BeforeEach
	void createVectorStoreTable() {
		this.jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS vector_store (
					id UUID NOT NULL DEFAULT uuid() PRIMARY KEY,
					content TEXT,
					metadata JSON,
					embedding VECTOR(1536) NOT NULL,
					VECTOR INDEX (embedding)
				) ENGINE=InnoDB""");
	}

	@Test
	void validateTableSchemaWithDefaultSchema() {
		assertThatNoException().isThrownBy(() -> this.schemaValidator.validateTableSchema(null, "vector_store", "id",
				"content", "metadata", "embedding", 1536));
	}

	@Test
	void validateTableSchemaWithExplicitSchemaName() {
		assertThatNoException().isThrownBy(() -> this.schemaValidator.validateTableSchema("testdb", "vector_store",
				"id", "content", "metadata", "embedding", 1536));
	}

	@Test
	void validateTableSchemaFailsOnMissingTableWithDefaultSchema() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.schemaValidator.validateTableSchema(null, "missing_vector_store", "id", "content",
					"metadata", "embedding", 1536))
			.withMessageContaining("Table 'missing_vector_store' does not exist in schema 'testdb'");
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		MariaDBSchemaValidator schemaValidator(JdbcTemplate jdbcTemplate) {
			return new MariaDBSchemaValidator(jdbcTemplate);
		}

	}

}
