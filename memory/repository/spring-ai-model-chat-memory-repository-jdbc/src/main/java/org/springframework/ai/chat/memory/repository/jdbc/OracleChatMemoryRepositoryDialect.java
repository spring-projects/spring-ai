/*
 * Copyright 2025-2025 the original author or authors.
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

/**
 * Dialect for Oracle.
 *
 * @author Xiaotong Fan
 * @author Pablo Silberkasten
 * @since 1.1.0
 */

public class OracleChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

	@Override
	public String getSelectMessagesSql() {
    // Adding the third column to match index 3 in JdbcChatMemoryRepository
    	return "SELECT content, type, \"TIMESTAMP\" FROM SPRING_AI_CHAT_MEMORY WHERE CONVERSATION_ID = ? ORDER BY \"TIMESTAMP\" ASC";
	}

	@Override
	public String getInsertMessageSql() {
		return "INSERT INTO SPRING_AI_CHAT_MEMORY (CONVERSATION_ID, CONTENT, TYPE, \"TIMESTAMP\") VALUES (?, ?, ?, ?)";
	}

	@Override
	public String getSelectConversationIdsSql() {
		return "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY";
	}

	@Override
	public String getDeleteMessagesSql() {
		return "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE CONVERSATION_ID = ?";
	}

}
