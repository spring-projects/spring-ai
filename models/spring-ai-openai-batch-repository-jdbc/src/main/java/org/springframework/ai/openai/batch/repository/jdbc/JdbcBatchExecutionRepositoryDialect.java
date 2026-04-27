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

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.support.JdbcUtils;

/**
 * Abstraction for database-specific SQL for batch execution repository.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public interface JdbcBatchExecutionRepositoryDialect {

	Logger logger = LoggerFactory.getLogger(JdbcBatchExecutionRepositoryDialect.class);

	String SELECT_COLUMNS = "batch_id, endpoint, status, request_count, input_file_id, created_at, updated_at";

	/**
	 * Returns the SQL to find a batch execution by ID.
	 */
	default String getSelectByIdSql() {
		return "SELECT " + SELECT_COLUMNS + " FROM SPRING_AI_BATCH_EXECUTION WHERE batch_id = ?";
	}

	/**
	 * Returns the SQL to find batch executions by status.
	 */
	default String getSelectByStatusSql() {
		return "SELECT " + SELECT_COLUMNS + " FROM SPRING_AI_BATCH_EXECUTION WHERE status = ?";
	}

	/**
	 * Returns the SQL to find all pending (non-terminal) batch executions.
	 */
	default String getSelectPendingExecutionsSql() {
		return "SELECT " + SELECT_COLUMNS
				+ " FROM SPRING_AI_BATCH_EXECUTION WHERE status NOT IN ('RESULTS_PROCESSED', 'FAILED', 'EXPIRED', 'CANCELLED')";
	}

	/**
	 * Returns the database-specific upsert SQL.
	 */
	String getUpsertSql();

	/**
	 * Returns the SQL to delete a batch execution by ID.
	 */
	default String getDeleteByIdSql() {
		return "DELETE FROM SPRING_AI_BATCH_EXECUTION WHERE batch_id = ?";
	}

	/**
	 * Detects the dialect from the DataSource.
	 */
	static JdbcBatchExecutionRepositoryDialect from(DataSource dataSource) {
		String productName = null;
		try {
			productName = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
		}
		catch (Exception e) {
			logger.warn("Due to failure in establishing JDBC connection or parsing metadata, the JDBC database vendor "
					+ "could not be determined", e);
		}
		if (productName == null || productName.trim().isEmpty()) {
			logger.warn("Database product name is null or empty, defaulting to Postgres dialect.");
			return new PostgresBatchExecutionRepositoryDialect();
		}
		return switch (productName) {
			case "PostgreSQL" -> new PostgresBatchExecutionRepositoryDialect();
			case "MySQL", "MariaDB" -> new MysqlBatchExecutionRepositoryDialect();
			case "Microsoft SQL Server" -> new SqlServerBatchExecutionRepositoryDialect();
			case "HSQL Database Engine" -> new HsqldbBatchExecutionRepositoryDialect();
			case "SQLite" -> new SqliteBatchExecutionRepositoryDialect();
			case "H2" -> new H2BatchExecutionRepositoryDialect();
			case "Oracle" -> new OracleBatchExecutionRepositoryDialect();
			default -> new PostgresBatchExecutionRepositoryDialect();
		};
	}

}
