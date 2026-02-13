/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
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
 * @author Yanming Zhou
 */
@Testcontainers
@JdbcTest
public class PgVectorSchemaValidatorIT {

	@ServiceConnection
	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PgVectorSchemaValidator schemaValidator;

	@Test
	void validateDimensions() {
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		this.jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS public.vector_store (
					id serial PRIMARY KEY,
					content text,
					metadata json,
					embedding vector(1024)
				)
				""");
		assertThatNoException()
			.isThrownBy(() -> this.schemaValidator.validateTableSchema("public", "vector_store", 1024));
		assertThatIllegalStateException()
			.isThrownBy(() -> this.schemaValidator.validateTableSchema("public", "vector_store", 2048))
			.withMessageContaining("1024");

	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		PgVectorSchemaValidator schemaValidator(JdbcTemplate jdbcTemplate) {
			return new PgVectorSchemaValidator(jdbcTemplate);
		}

	}

}
