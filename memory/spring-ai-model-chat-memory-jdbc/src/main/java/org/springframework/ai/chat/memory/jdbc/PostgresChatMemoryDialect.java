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

/**
 * Dialect for Postgres.
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
public class PostgresChatMemoryDialect implements JdbcChatMemoryDialect {

	@Override
	public String getSelectMessagesSql() {
		return "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY \"timestamp\"";
	}

	@Override
	public String getInsertMessageSql() {
		return "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, \"timestamp\") VALUES (?, ?, ?, ?)";
	}

	@Override
	public String getSelectConversationIdsSql() {
		return "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY";
	}

	@Override
	public String getDeleteMessagesSql() {
		return "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?";
	}

}
