/*
 * Copyright 2023-2024 the original author or authors.
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

/**
 * @author Diego Dupin
 * @since 1.0.0
 */
public class MariaDBSchemaValidator {

	private static final Logger logger = LoggerFactory.getLogger(MariaDBSchemaValidator.class);

	private final JdbcTemplate jdbcTemplate;

	public MariaDBSchemaValidator(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private boolean isTableExists(@Nullable String schemaName, String tableName) {
		// schema and table are expected to be escaped
		String sql = "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
		try {
			// Query for a single integer value, if it exists, table exists
			this.jdbcTemplate.queryForObject(sql, Integer.class, (schemaName == null) ? "SCHEMA()" : schemaName,
					tableName);
			return true;
		}
		catch (DataAccessException e) {
			return false;
		}
	}

	void validateTableSchema(@Nullable String schemaName, String tableName, String idFieldName, String contentFieldName,
			String metadataFieldName, String embeddingFieldName, int embeddingDimensions) {

		if (!isTableExists(schemaName, tableName)) {
			throw new IllegalStateException(
					String.format("Table '%s' does not exist in schema '%s'", tableName, schemaName));
		}

		// ensure server support VECTORs
		try {
			// Query for a single integer value, if it exists, database support vector
			this.jdbcTemplate.queryForObject("SELECT vec_distance_euclidean(x'0000803f', x'0000803f')", Integer.class,
					schemaName, tableName);
		}
		catch (DataAccessException e) {
			logger.error("Error while validating database vector support {}", e.getMessage());
			logger.error("""
					Failed to validate that database supports VECTOR.
					Run the following SQL commands:
					   SELECT @@version;
					And ensure that version is >= 11.7.1""");
			throw new IllegalStateException(e);
		}

		try {
			logger.info("Validating MariaDBStore schema for table: {} in schema: {}", tableName, schemaName);

			List<String> expectedColumns = new ArrayList<>();
			expectedColumns.add(idFieldName);
			expectedColumns.add(contentFieldName);
			expectedColumns.add(metadataFieldName);
			expectedColumns.add(embeddingFieldName);

			// Query to check if the table exists with the required fields and types
			// Include the schema name in the query to target the correct table
			String query = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
					+ "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
			List<Map<String, @Nullable Object>> columns = this.jdbcTemplate.queryForList(query, schemaName, tableName);

			if (columns.isEmpty()) {
				throw new IllegalStateException("Error while validating table schema, Table " + tableName
						+ " does not exist in schema " + schemaName);
			}

			// Check each column against expected fields
			List<String> availableColumns = new ArrayList<>();
			for (Map<String, Object> column : columns) {
				String columnName = (String) column.get("COLUMN_NAME");
				Assert.state(columnName != null, "COLUMN_NAME result should not be null");
				columnName = validateAndEnquoteIdentifier(columnName, false);
				availableColumns.add(columnName);
			}

			// TODO ensure id is a primary key for batch update

			expectedColumns.removeAll(availableColumns);

			if (expectedColumns.isEmpty()) {
				logger.info("MariaDB VectorStore schema validation successful");
			}
			else {
				throw new IllegalStateException("Missing fields " + expectedColumns);
			}

		}
		catch (DataAccessException | IllegalStateException e) {
			logger.error("Error while validating table schema{}", e.getMessage());
			logger.error("Failed to operate with the specified table in the database. To resolve this issue,"
					+ " please ensure the following steps are completed:\n"
					+ "1. Verify that the table exists with the appropriate structure. If it does not"
					+ " exist, create it using a SQL command similar to the following:\n"
					+ String.format("""
							  CREATE TABLE IF NOT EXISTS %s (
									%s UUID NOT NULL DEFAULT uuid() PRIMARY KEY,
									%s TEXT,
									%s JSON,
									%s VECTOR(%d) NOT NULL,
									VECTOR INDEX (%s)
							) ENGINE=InnoDB""", schemaName == null ? tableName : schemaName + "." + tableName,
							idFieldName, contentFieldName, metadataFieldName, embeddingFieldName, embeddingDimensions,
							embeddingFieldName)
					+ "\n" + "Please adjust these commands based on your specific configuration and the"
					+ " capabilities of your vector database system.");
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Escaped identifier according to MariaDB requirement.
	 * @param identifier identifier
	 * @param alwaysQuote indicate if identifier must be quoted even if not necessary.
	 * @return return escaped identifier, quoted when necessary or indicated with
	 * alwaysQuote.
	 * @see <a href="https://mariadb.com/kb/en/library/identifier-names/">mariadb
	 * identifier name</a>
	 */
	// @Override when not supporting java 8
	public static String validateAndEnquoteIdentifier(String identifier, boolean alwaysQuote) {
		try {
			String quotedId = Driver.enquoteIdentifier(identifier, alwaysQuote);
			// force use of simple table name
			if (Pattern.compile("`?[\\p{Alnum}_]*`?").matcher(identifier).matches()) {
				return quotedId;
			}
			throw new IllegalArgumentException(String
				.format("Identifier '%s' should only contain alphanumeric characters and underscores", quotedId));
		}
		catch (SQLException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
