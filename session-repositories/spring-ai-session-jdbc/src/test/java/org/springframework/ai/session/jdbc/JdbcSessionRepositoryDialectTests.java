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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JdbcSessionRepositoryDialect} implementations, focusing on SQL
 * correctness for dialect-specific fragments.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryDialectTests {

	// -------------------------------------------------------------------------
	// from(DataSource) — detection
	// -------------------------------------------------------------------------

	@Test
	void detectsSupportedDatabases() throws SQLException {
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("PostgreSQL")))
			.isInstanceOf(PostgresJdbcSessionRepositoryDialect.class);
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("H2")))
			.isInstanceOf(H2JdbcSessionRepositoryDialect.class);
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("HSQL Database Engine")))
			.isInstanceOf(HsqldbJdbcSessionRepositoryDialect.class);
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("MySQL")))
			.isInstanceOf(MysqlJdbcSessionRepositoryDialect.class);
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("MariaDB")))
			.isInstanceOf(MysqlJdbcSessionRepositoryDialect.class);
		assertThat(JdbcSessionRepositoryDialect.from(dataSourceReporting("Microsoft SQL Server")))
			.isInstanceOf(SqlServerJdbcSessionRepositoryDialect.class);
	}

	// -------------------------------------------------------------------------
	// getLimitClause()
	// -------------------------------------------------------------------------

	@Test
	void limitClauseDefaultsToLimit() {
		assertThat(new PostgresJdbcSessionRepositoryDialect().getLimitClause()).isEqualTo("LIMIT ?");
		assertThat(new H2JdbcSessionRepositoryDialect().getLimitClause()).isEqualTo("LIMIT ?");
		assertThat(new MysqlJdbcSessionRepositoryDialect().getLimitClause()).isEqualTo("LIMIT ?");
	}

	@Test
	void sqlServerLimitsRowsWithOffsetFetch() {
		SqlServerJdbcSessionRepositoryDialect dialect = new SqlServerJdbcSessionRepositoryDialect();
		assertThat(dialect.getLimitClause()).isEqualTo("OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
		// MERGE must end with a semicolon on SQL Server, and an update keeps created_at
		assertThat(dialect.getUpsertSessionSql().strip()).startsWith("MERGE INTO AI_SESSION").endsWith(";");
		assertThat(dialect.getUpsertSessionSql()).contains("SET expires_at = s.expires_at, metadata = s.metadata")
			.doesNotContain("created_at = s.created_at");
	}

	@Test
	void sqlServerInsertsIfAbsentWithMergeThatNeverUpdates() {
		String sql = new SqlServerJdbcSessionRepositoryDialect().getInsertSessionIfAbsentSql();
		// An existing id is reported through the update count, not a duplicate-key error
		assertThat(sql.strip()).startsWith("MERGE INTO AI_SESSION WITH (HOLDLOCK)").endsWith(";");
		assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)")
			.doesNotContain("WHEN MATCHED")
			.doesNotContain("UPDATE");
	}

	@Test
	void sqlServerMatchesSessionIdWithoutCastingIt() {
		SqlServerJdbcSessionRepositoryDialect dialect = new SqlServerJdbcSessionRepositoryDialect();
		// A cast silently cuts an id longer than its type, possibly to another session's
		assertThat(dialect.getUpsertSessionSql()).contains("USING (SELECT ? AS id,").contains("ON t.id = s.id");
		assertThat(dialect.getInsertSessionIfAbsentSql()).contains("USING (SELECT ? AS id,").contains("ON t.id = s.id");
	}

	@Test
	void unsupportedDatabaseFailsFast() throws SQLException {
		DataSource oracle = dataSourceReporting("Oracle");
		assertThatIllegalStateException().isThrownBy(() -> JdbcSessionRepositoryDialect.from(oracle))
			.withMessageContaining("Oracle")
			.withMessageContaining("dialect(");
	}

	@Test
	void undeterminableDatabaseFailsFast() throws SQLException {
		DataSource unreachable = mock(DataSource.class);
		given(unreachable.getConnection()).willThrow(new SQLException("connection refused"));
		assertThatIllegalStateException().isThrownBy(() -> JdbcSessionRepositoryDialect.from(unreachable));
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
