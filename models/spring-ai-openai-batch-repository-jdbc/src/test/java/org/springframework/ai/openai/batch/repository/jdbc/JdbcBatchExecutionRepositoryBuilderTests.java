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

package org.springframework.ai.openai.batch.repository.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JdbcBatchExecutionRepository.Builder}.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
class JdbcBatchExecutionRepositoryBuilderTests {

	@Test
	void testBuilderWithExplicitDialect() {
		DataSource dataSource = mock(DataSource.class);
		JdbcBatchExecutionRepositoryDialect dialect = mock(JdbcBatchExecutionRepositoryDialect.class);

		JdbcBatchExecutionRepository repository = JdbcBatchExecutionRepository.builder()
			.dataSource(dataSource)
			.dialect(dialect)
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithExplicitDialectAndTransactionManager() {
		DataSource dataSource = mock(DataSource.class);
		JdbcBatchExecutionRepositoryDialect dialect = mock(JdbcBatchExecutionRepositoryDialect.class);
		PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

		JdbcBatchExecutionRepository repository = JdbcBatchExecutionRepository.builder()
			.dataSource(dataSource)
			.dialect(dialect)
			.transactionManager(txManager)
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithDialectFromDataSource() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/testdb");

		JdbcBatchExecutionRepository repository = JdbcBatchExecutionRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithNullDataSource() {
		assertThatThrownBy(() -> JdbcBatchExecutionRepository.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("DataSource must be set (either via dataSource() or jdbcTemplate())");
	}

	@Test
	void repositoryShouldUseProvidedJdbcTemplate() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		JdbcBatchExecutionRepository repository = JdbcBatchExecutionRepository.builder()
			.jdbcTemplate(jdbcTemplate)
			.build();

		assertThat(repository).extracting("jdbcTemplate").isSameAs(jdbcTemplate);
	}

}
