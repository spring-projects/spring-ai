/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

/**
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
public class PgVectorChatMemoryConfig {

	private static final Logger logger = LoggerFactory.getLogger(PgVectorChatMemoryConfig.class);

	public static final boolean DEFAULT_SCHEMA_INITIALIZATION = false;

	public static final String DEFAULT_SCHEMA_NAME = "public";

	public static final String DEFAULT_TABLE_NAME = "ai_chat_memory";

	public static final String DEFAULT_SESSION_ID_COLUMN_NAME = "session_id";

	public static final String DEFAULT_EXCHANGE_ID_COLUMN_NAME = "message_timestamp";

	public static final String DEFAULT_ASSISTANT_COLUMN_NAME = "assistant";

	// "user" is a reserved keyword in postgres, hence the double quotes.
	public static final String DEFAULT_USER_COLUMN_NAME = "\"user\"";

	private final boolean initializeSchema;

	private final String schemaName;

	private final String tableName;

	private final String sessionIdColumnName;

	private final String exchangeIdColumnName;

	private final String assistantColumnName;

	private final String userColumnName;

	private final JdbcTemplate jdbcTemplate;

	private PgVectorChatMemoryConfig(Builder builder) {
		this.initializeSchema = builder.initializeSchema;
		this.schemaName = builder.schemaName;
		this.tableName = builder.tableName;
		this.sessionIdColumnName = builder.sessionIdColumnName;
		this.exchangeIdColumnName = builder.exchangeIdColumnName;
		this.assistantColumnName = builder.assistantColumnName;
		this.userColumnName = builder.userColumnName;
		this.jdbcTemplate = builder.jdbcTemplate;
	}

	public static Builder builder() {
		return new Builder();
	}

	String getFullyQualifiedTableName() {
		return this.schemaName + "." + this.tableName;
	}

	String getSchemaName() {
		return this.schemaName;
	}

	String getTableName() {
		return this.tableName;
	}

	String getSessionIdColumnName() {
		return this.sessionIdColumnName;
	}

	String getExchangeIdColumnName() {
		return this.exchangeIdColumnName;
	}

	String getAssistantColumnName() {
		return this.assistantColumnName;
	}

	String getUserColumnName() {
		return this.userColumnName;
	}

	JdbcTemplate getJdbcTemplate() {
		return this.jdbcTemplate;
	}

	void initializeSchema() {
		if (!this.initializeSchema) {
			logger.warn("Skipping the schema initialization for table: {}", this.getFullyQualifiedTableName());
			return;
		}

		logger.info("Initializing PGChatMemory schema for table: {} in schema: {}", this.getTableName(),
				this.getSchemaName());

		var indexName = String
			.format("%s_%s_%s_idx", this.getTableName(), this.getSessionIdColumnName(), this.getExchangeIdColumnName())
			// Keywords in postgres has to be wrapped in double quotes. It is possible
			// that the table or column may be a reserved keyword. If so, just remove
			// them.
			.replaceAll("\"", "");

		this.jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", this.getSchemaName()));
		this.jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					%s character varying(40) NOT NULL,
					%s timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
					%s text,
					%s text
				)
				""", this.getFullyQualifiedTableName(), this.getSessionIdColumnName(), this.getExchangeIdColumnName(),
				this.getAssistantColumnName(), this.getUserColumnName()));
		this.jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s, %s DESC)", indexName,
				this.getFullyQualifiedTableName(), this.getSessionIdColumnName(), this.getExchangeIdColumnName()));
	}

	public static class Builder {

		private boolean initializeSchema = DEFAULT_SCHEMA_INITIALIZATION;

		private String schemaName = DEFAULT_SCHEMA_NAME;

		private String tableName = DEFAULT_TABLE_NAME;

		private String sessionIdColumnName = DEFAULT_SESSION_ID_COLUMN_NAME;

		private String exchangeIdColumnName = DEFAULT_EXCHANGE_ID_COLUMN_NAME;

		private String assistantColumnName = DEFAULT_ASSISTANT_COLUMN_NAME;

		private String userColumnName = DEFAULT_USER_COLUMN_NAME;

		private JdbcTemplate jdbcTemplate;

		private Builder() {
		}

		public Builder withInitializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public Builder withSchemaName(String schemaName) {
			Assert.hasText(schemaName, "schema name must not be empty");

			this.schemaName = schemaName;
			return this;
		}

		public Builder withTableName(String tableName) {
			Assert.hasText(tableName, "table name must not be empty");

			this.tableName = tableName;
			return this;
		}

		public Builder withSessionIdColumnName(String sessionIdColumnName) {
			Assert.hasText(sessionIdColumnName, "session id column name must not be empty");

			this.sessionIdColumnName = sessionIdColumnName;
			return this;
		}

		public Builder withExchangeIdColumnName(String exchangeIdColumnName) {
			Assert.hasText(exchangeIdColumnName, "exchange id column name must not be empty");

			this.exchangeIdColumnName = exchangeIdColumnName;
			return this;
		}

		public Builder withAssistantColumnName(String assistantColumnName) {
			Assert.hasText(assistantColumnName, "assistant column name must not be empty");

			this.assistantColumnName = assistantColumnName;
			return this;
		}

		public Builder withUserColumnName(String userColumnName) {
			Assert.hasText(userColumnName, "user column name must not be empty");

			this.userColumnName = userColumnName;
			return this;
		}

		public Builder withJdbcTemplate(JdbcTemplate jdbcTemplate) {
			Assert.notNull(jdbcTemplate, "jdbc template must not be null");

			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		public PgVectorChatMemoryConfig build() {
			Assert.notNull(jdbcTemplate, "jdbc template must not be null");

			return new PgVectorChatMemoryConfig(this);
		}

	}

}
