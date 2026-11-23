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

package org.springframework.ai.session.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Tests for {@link JdbcSessionRepository.Builder}.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryBuilderTests {

	@Test
	void explicitDialectNeverOpensConnection() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		given(dataSource.getConnection()).willThrow(new SQLException("not now"));

		JdbcSessionRepository repository = JdbcSessionRepository.builder()
			.dataSource(dataSource)
			.dialect(new PostgresJdbcSessionRepositoryDialect())
			.build();

		assertThat(repository).isNotNull();
		then(dataSource).should(never()).getConnection();
	}

	@Test
	void dataSourceComesFromJdbcTemplate() throws SQLException {
		DataSource dataSource = dataSourceReporting("H2");

		JdbcSessionRepository repository = JdbcSessionRepository.builder()
			.jdbcTemplate(new JdbcTemplate(dataSource))
			.build();

		assertThat(repository).isNotNull();
	}

	@Test
	void withoutDataSourceBuildFails() {
		assertThatIllegalArgumentException().isThrownBy(() -> JdbcSessionRepository.builder().build())
			.withMessageContaining("DataSource");
	}

	@Test
	void unreachableDataSourceWithoutDialectFailsFast() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));

		assertThatIllegalStateException()
			.isThrownBy(() -> JdbcSessionRepository.builder().dataSource(dataSource).build());
	}

	@Test
	void unsupportedDatabaseWithoutDialectFailsFast() throws SQLException {
		DataSource oracle = dataSourceReporting("Oracle");

		assertThatIllegalStateException().isThrownBy(() -> JdbcSessionRepository.builder().dataSource(oracle).build())
			.withMessageContaining("Oracle")
			.withMessageContaining("dialect(");
	}

	private static DataSource dataSourceReporting(String productName) throws SQLException {
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		given(metaData.getDatabaseProductName()).willReturn(productName);
		Connection connection = mock(Connection.class);
		given(connection.getMetaData()).willReturn(metaData);
		DataSource dataSource = mock(DataSource.class);
		given(dataSource.getConnection()).willReturn(connection);
		return dataSource;
	}

}
