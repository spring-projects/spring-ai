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

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.jdbc.support.JdbcUtils;

/**
 * The SQL that differs between databases, for {@link JdbcSessionRepository}: the upsert
 * and the insert-if-absent of a session. Every other statement is shared and lives in the
 * repository. {@link #from(DataSource)} detects the dialect from the database metadata.
 *
 * @see PostgresJdbcSessionRepositoryDialect
 * @see H2JdbcSessionRepositoryDialect
 * @see HsqldbJdbcSessionRepositoryDialect
 * @see MysqlJdbcSessionRepositoryDialect
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface JdbcSessionRepositoryDialect {

	/**
	 * Inserts a row into {@code AI_SESSION} or updates the existing one, refreshing only
	 * {@code expires_at} and {@code metadata}: {@code created_at} keeps its value and
	 * {@code event_version} is never touched. Parameters, in order: {@code id}, {@code
	 * created_at}, {@code expires_at}, {@code metadata}.
	 */
	String getUpsertSessionSql();

	/**
	 * Inserts a row into {@code AI_SESSION} only if its id does not exist, with the same
	 * four parameters as {@link #getUpsertSessionSql()}: one row updated on insert, zero
	 * rows or a duplicate-key error when the id exists. The default is a plain {@code
	 * INSERT}; a database whose failed statement aborts the enclosing transaction,
	 * PostgreSQL, uses a conflict-ignoring form.
	 */
	default String getInsertSessionIfAbsentSql() {
		return "INSERT INTO AI_SESSION (id, created_at, expires_at, metadata) VALUES (?, ?, ?, ?)";
	}

	/**
	 * The dialect of the database the data source connects to, from its product name:
	 * PostgreSQL, H2, HSQLDB, MySQL or MariaDB.
	 * @throws IllegalStateException if the product name cannot be read or the database is
	 * not supported; set the dialect with
	 * {@link JdbcSessionRepository.Builder#dialect(JdbcSessionRepositoryDialect)} then
	 */
	static JdbcSessionRepositoryDialect from(DataSource dataSource) {
		String productName;
		try {
			productName = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not determine the database product name to select a "
					+ "JdbcSessionRepositoryDialect; set one explicitly via JdbcSessionRepository.builder().dialect(...)",
					ex);
		}
		if (productName == null || productName.isBlank()) {
			throw new IllegalStateException(
					"Database product name is null or blank; set a JdbcSessionRepositoryDialect "
							+ "explicitly via JdbcSessionRepository.builder().dialect(...)");
		}
		return switch (productName) {
			case "PostgreSQL" -> new PostgresJdbcSessionRepositoryDialect();
			case "H2" -> new H2JdbcSessionRepositoryDialect();
			case "HSQL Database Engine" -> new HsqldbJdbcSessionRepositoryDialect();
			case "MySQL", "MariaDB" -> new MysqlJdbcSessionRepositoryDialect();
			default -> throw new IllegalStateException("No JdbcSessionRepositoryDialect for database '" + productName
					+ "'; supported: PostgreSQL, H2, HSQLDB, MySQL, MariaDB. Implement JdbcSessionRepositoryDialect and set it "
					+ "via JdbcSessionRepository.builder().dialect(...)");
		};
	}

}
