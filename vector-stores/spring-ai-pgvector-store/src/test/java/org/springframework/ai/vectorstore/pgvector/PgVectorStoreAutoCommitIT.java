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

package org.springframework.ai.vectorstore.pgvector;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link PgVectorStore} schema initialization with a connection
 * pool that has auto-commit disabled.
 *
 * @author Harshvardhan Singh
 */
@Testcontainers
class PgVectorStoreAutoCommitIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	@ParameterizedTest(name = "autoCommit={0}")
	@ValueSource(booleans = { true, false })
	void shouldInitializeSchemaRegardlessOfAutoCommit(boolean autoCommit) {
		try (HikariDataSource dataSource = new HikariDataSource()) {
			dataSource.setJdbcUrl(postgresContainer.getJdbcUrl());
			dataSource.setUsername(postgresContainer.getUsername());
			dataSource.setPassword(postgresContainer.getPassword());
			dataSource.setAutoCommit(autoCommit);

			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			String tableName = "auto_commit_" + autoCommit;

			PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, mock(EmbeddingModel.class))
				.vectorTableName(tableName)
				.dimensions(3)
				.initializeSchema(true)
				.build();
			vectorStore.afterPropertiesSet();

			assertThat(jdbcTemplate.queryForObject(
					"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)",
					Boolean.class, tableName))
				.isTrue();
		}
	}

}
