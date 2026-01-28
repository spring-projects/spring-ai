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

package org.springframework.ai.vectorstore.pgvector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validates the schema of a PostgreSQL table used as a PGVectorStore.
 *
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 * @since 1.0.0
 */
class PgVectorSchemaValidator {

	private static final Logger logger = LoggerFactory.getLogger(PgVectorSchemaValidator.class);

	private final JdbcTemplate jdbcTemplate;

	PgVectorSchemaValidator(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	static boolean isValidNameForDatabaseObject(String name) {

		if (name == null) {
			return false;
		}

		// Check if the table or schema has Only alphanumeric characters and underscores
		// and should be less than 64 characters
		if (!name.matches("^[a-zA-Z0-9_]{1,64}$")) {
			return false;
		}

		// Check to ensure the table or schema name is not purely numeric
		if (name.matches("^[0-9]+$")) {
			return false;
		}

		return true;

	}

	boolean isTableExists(String schemaName, String tableName) {
		String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
		try {
			// Query for a single integer value, if it exists, table exists
			this.jdbcTemplate.queryForObject(sql, Integer.class, schemaName, tableName);
			return true;
		}
		catch (DataAccessException e) {
			return false;
		}
	}

	void validateTableSchema(String schemaName, String tableName) {

		if (!isValidNameForDatabaseObject(schemaName)) {
			throw new IllegalArgumentException(
					"Schema name should only contain alphanumeric characters and underscores");
		}
		if (!isValidNameForDatabaseObject(tableName)) {
			throw new IllegalArgumentException(
					"Table name should only contain alphanumeric characters and underscores");
		}

		if (!isTableExists(schemaName, tableName)) {
			throw new IllegalStateException("Table " + tableName + " does not exist in schema " + schemaName);
		}

		try {
			logger.info("Validating PGVectorStore schema for table: {} in schema: {}", tableName, schemaName);

			List<String> expectedColumns = new ArrayList<>();
			expectedColumns.add("id");
			expectedColumns.add("content");
			expectedColumns.add("metadata");
			expectedColumns.add("embedding");

			// Query to check if the table exists with the required fields and types
			// Include the schema name in the query to target the correct table
			String query = "SELECT column_name, data_type FROM information_schema.columns "
					+ "WHERE table_schema = ? AND table_name = ?";
			List<Map<String, @Nullable Object>> columns = this.jdbcTemplate.queryForList(query,
					new Object[] { schemaName, tableName });

			if (columns.isEmpty()) {
				throw new IllegalStateException("Error while validating table schema, Table " + tableName
						+ " does not exist in schema " + schemaName);
			}

			// Check each column against expected fields
			List<String> availableColumns = new ArrayList<>();
			for (Map<String, Object> column : columns) {
				String columnName = (String) column.get("column_name");
				availableColumns.add(columnName);

			}

			expectedColumns.removeAll(availableColumns);

			if (expectedColumns.isEmpty()) {
				logger.info("PG VectorStore schema validation successful");
			}
			else {
				throw new IllegalStateException("Missing fields " + expectedColumns);
			}

		}
		catch (DataAccessException | IllegalStateException e) {
			logger.error("Error while validating table schema{}", e.getMessage());
			logger
				.error("Failed to operate with the specified table in the database. To resolve this issue, please ensure the following steps are completed:\n"
						+ "1. Ensure the necessary PostgreSQL extensions are enabled. Run the following SQL commands:\n"
						+ "   CREATE EXTENSION IF NOT EXISTS vector;\n" + "   CREATE EXTENSION IF NOT EXISTS hstore;\n"
						+ "   CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n"
						+ "2. Verify that the table exists with the appropriate structure. If it does not exist, create it using a SQL command similar to the following, replacing 'embedding_dimensions' with the appropriate size based on your vector embeddings:\n"
						+ String.format("   CREATE TABLE IF NOT EXISTS %s (\n"
								+ "       id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,\n" + "       content text,\n"
								+ "       metadata json,\n"
								+ "       embedding vector(embedding_dimensions)  // Replace 'embedding_dimensions' with your specific value\n"
								+ "   );\n", schemaName + "." + tableName)
						+ "3. Create an appropriate index for the vector embedding to optimize performance. Adjust the index type and options based on your usage. Example SQL for creating an index:\n"
						+ String.format("   CREATE INDEX ON %s USING HNSW (embedding vector_cosine_ops);\n", tableName)
						+ "\nPlease adjust these commands based on your specific configuration and the capabilities of your vector database system.");
			throw new IllegalStateException(e);

		}
	}

}
