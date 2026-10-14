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
 * Abstraction for database-specific SQL used by {@link JdbcSessionRepository}.
 *
 * <p>
 * Each method returns a parameterised SQL string compatible with
 * {@link org.springframework.jdbc.core.JdbcTemplate}. Implementations cover only the SQL
 * surface that differs across databases (upsert syntax, boolean literals, keyword
 * search). Generic queries shared by all dialects live directly in
 * {@link JdbcSessionRepository}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see PostgresJdbcSessionRepositoryDialect
 * @see H2JdbcSessionRepositoryDialect
 * @see MysqlJdbcSessionRepositoryDialect
 */
public interface JdbcSessionRepositoryDialect {

	/**
	 * Upsert a row in {@code AI_SESSION}. The statement must insert a new row or update
	 * an existing one. On update only {@code expires_at} and {@code metadata} are
	 * refreshed — {@code created_at} keeps its original value and {@code event_version}
	 * must <em>not</em> be modified.
	 *
	 * <p>
	 * Parameters (in order): {@code id}, {@code created_at}, {@code expires_at},
	 * {@code metadata}.
	 */
	String getUpsertSessionSql();

	/**
	 * Inserts a row into {@code AI_SESSION} only if its {@code id} does not exist yet.
	 * Same four parameters as {@link #getUpsertSessionSql()}. Must update one row on
	 * insert and either update zero rows or fail with a duplicate-key error when the id
	 * exists. The default is a plain {@code INSERT} (duplicate key error); dialects whose
	 * failed statements abort the enclosing transaction (PostgreSQL) should use a
	 * conflict-ignoring form instead.
	 */
	default String getInsertSessionIfAbsentSql() {
		return "INSERT INTO AI_SESSION (id, created_at, expires_at, metadata) VALUES (?, ?, ?, ?)";
	}

	/**
	 * Detects the dialect from the database product name reported by the
	 * {@link DataSource}'s metadata. Supports PostgreSQL, H2, MySQL and MariaDB.
	 * @throws IllegalStateException if the product name cannot be determined or the
	 * database is not supported; configure the dialect explicitly with
	 * {@link JdbcSessionRepository.Builder#dialect(JdbcSessionRepositoryDialect)} instead
	 * of relying on detection
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
			case "MySQL", "MariaDB" -> new MysqlJdbcSessionRepositoryDialect();
			default -> throw new IllegalStateException("No JdbcSessionRepositoryDialect for database '" + productName
					+ "'; supported: PostgreSQL, H2, MySQL, MariaDB. Implement JdbcSessionRepositoryDialect and set it "
					+ "via JdbcSessionRepository.builder().dialect(...)");
		};
	}

}
