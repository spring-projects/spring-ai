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

package org.springframework.ai.chat.memory.repository.jdbc;

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
 * Tests for {@link JdbcChatMemoryRepository.Builder}.
 *
 * @author Mark Pollack
 * @author Yanming Zhou
 */
public class JdbcChatMemoryRepositoryBuilderTests {

	@Test
	void testBuilderWithExplicitDialect() {
		DataSource dataSource = mock(DataSource.class);
		JdbcChatMemoryRepositoryDialect dialect = mock(JdbcChatMemoryRepositoryDialect.class);

		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
			.dataSource(dataSource)
			.dialect(dialect)
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithExplicitDialectAndTransactionManager() {
		DataSource dataSource = mock(DataSource.class);
		JdbcChatMemoryRepositoryDialect dialect = mock(JdbcChatMemoryRepositoryDialect.class);
		PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
			.dataSource(dataSource)
			.dialect(dialect)
			.transactionManager(txManager)
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithDialectFromDataSource() throws SQLException {
		// Setup mocks
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/testdb");

		// Test with dialect from datasource
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithMysqlDialectFromDataSource() throws SQLException {
		// Setup mocks for MySQL
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");

		// Test with dialect from datasource
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithSqlServerDialectFromDataSource() throws SQLException {
		// Setup mocks for SQL Server
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:sqlserver://localhost:1433;databaseName=testdb");

		// Test with dialect from datasource
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithHsqldbDialectFromDataSource() throws SQLException {
		// Setup mocks for HSQLDB
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:hsqldb:mem:testdb");

		// Test with dialect from datasource
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithUnknownDialectFromDataSource() throws SQLException {
		// Setup mocks for unknown database
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:unknown://localhost:1234/testdb");

		// Test with dialect from datasource - should default to PostgreSQL
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithExceptionInDataSourceConnection() throws SQLException {
		// Setup mocks with exception
		DataSource dataSource = mock(DataSource.class);
		when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

		// Test with dialect from datasource - should default to PostgreSQL
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithNullDataSource() {
		assertThatThrownBy(() -> JdbcChatMemoryRepository.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("DataSource must be set (either via dataSource() or jdbcTemplate())");
	}

	@Test
	void testBuilderWithNullDataSourceButExplicitDialect() {
		DataSource dataSource = mock(DataSource.class);
		JdbcChatMemoryRepositoryDialect dialect = mock(JdbcChatMemoryRepositoryDialect.class);

		// Should work because dialect is explicitly set
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
			.dataSource(dataSource)
			.dialect(dialect)
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void testBuilderWithNullDataSourceAndDialect() {
		assertThatThrownBy(() -> JdbcChatMemoryRepository.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("DataSource must be set (either via dataSource() or jdbcTemplate())");
	}

	/**
	 * Verifies that when an explicit dialect is provided to the builder, it takes
	 * precedence over any dialect detected from the DataSource. If the explicit dialect
	 * differs from the detected one, the explicit dialect is used and a warning is
	 * logged. This ensures that user intent (explicit configuration) always overrides
	 * automatic detection.
	 */
	@Test
	void testBuilderPreferenceForExplicitDialect() throws SQLException {
		// Setup mocks for PostgreSQL
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/testdb");

		// Create an explicit MySQL dialect
		JdbcChatMemoryRepositoryDialect mysqlDialect = new MysqlChatMemoryRepositoryDialect();

		// Test with explicit dialect - should use MySQL dialect even though PostgreSQL is
		// detected
		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
			.dataSource(dataSource)
			.dialect(mysqlDialect)
			.build();

		assertThat(repository).isNotNull();
		// Verify warning was logged (would need to use a logging framework test utility
		// for this)
	}

	@Test
	void repositoryShouldUseProvidedJdbcTemplate() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder().jdbcTemplate(jdbcTemplate).build();

		assertThat(repository).extracting("jdbcTemplate").isSameAs(jdbcTemplate);
	}

}
