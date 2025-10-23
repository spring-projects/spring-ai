package org.springframework.ai.chat.memory.repository.jdbc;

public class OracleChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

	@Override
	public String getSelectMessagesSql() {
		return "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE CONVERSATION_ID = ? ORDER BY \"TIMESTAMP\"";
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
