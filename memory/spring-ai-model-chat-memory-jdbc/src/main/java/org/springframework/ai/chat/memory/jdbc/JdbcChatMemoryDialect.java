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

package org.springframework.ai.chat.memory.jdbc;

import javax.sql.DataSource;

/**
 * Abstraction for database-specific SQL for chat memory repository.
 */
public interface JdbcChatMemoryDialect {

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
	 * Detects the dialect from the DataSource or JDBC URL.
	 */
	static JdbcChatMemoryDialect from(DataSource dataSource) {
		// Simple detection (could be improved)
		try {
			String url = dataSource.getConnection().getMetaData().getURL().toLowerCase();
			if (url.contains("postgresql"))
				return new PostgresChatMemoryDialect();
			if (url.contains("mysql"))
				return new MysqlChatMemoryDialect();
			if (url.contains("mariadb"))
				return new MysqlChatMemoryDialect();
			if (url.contains("sqlserver"))
				return new SqlServerChatMemoryDialect();
			if (url.contains("hsqldb"))
				return new HsqldbChatMemoryDialect();
			// Add more as needed
		}
		catch (Exception ignored) {
		}
		return new PostgresChatMemoryDialect(); // default
	}

}
