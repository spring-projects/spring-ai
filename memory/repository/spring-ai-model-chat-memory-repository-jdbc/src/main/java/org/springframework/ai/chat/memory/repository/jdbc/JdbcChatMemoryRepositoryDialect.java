/*
 * Copyright 2024-2025 the original author or authors.
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

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.support.JdbcUtils;

/**
 * Abstraction for database-specific SQL for chat memory repository.
 */
public interface JdbcChatMemoryRepositoryDialect {

	Logger logger = LoggerFactory.getLogger(JdbcChatMemoryRepositoryDialect.class);

	/**
	 * Returns the SQL to fetch messages for a conversation, ordered by timestamp, with
	 * limit.
	 */
	String getSelectMessagesSql();

	/**
	 * Returns the SQL to insert a message.
	 */
	String getInsertMessageSql();

	/**
	 * Returns the SQL to fetch conversation IDs.
	 */
	String getSelectConversationIdsSql();

	/**
	 * Returns the SQL to delete all messages for a conversation.
	 */
	String getDeleteMessagesSql();

	/**
	 * Optionally, dialect can provide more advanced SQL as needed.
	 */

	/**
	 * Detects the dialect from the DataSource.
	 */
	static JdbcChatMemoryRepositoryDialect from(DataSource dataSource) {
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
			return new PostgresChatMemoryRepositoryDialect();
		}
		return switch (productName) {
			case "PostgreSQL" -> new PostgresChatMemoryRepositoryDialect();
			case "MySQL", "MariaDB" -> new MysqlChatMemoryRepositoryDialect();
			case "Microsoft SQL Server" -> new SqlServerChatMemoryRepositoryDialect();
			case "HSQL Database Engine" -> new HsqldbChatMemoryRepositoryDialect();
			default -> // Add more as needed
				new PostgresChatMemoryRepositoryDialect();
		};
	}

}
