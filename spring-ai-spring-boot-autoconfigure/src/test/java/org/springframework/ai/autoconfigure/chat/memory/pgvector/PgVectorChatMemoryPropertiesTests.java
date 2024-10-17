package org.springframework.ai.autoconfigure.chat.memory.pgvector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.PgVectorChatMemoryConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jonathan Leijendekker
 */
class PgVectorChatMemoryPropertiesTests {

	@Test
	void defaultValues() {
		var props = new PgVectorChatMemoryProperties();
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_SCHEMA_NAME, props.getSchemaName());
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_TABLE_NAME, props.getTableName());
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_SESSION_ID_COLUMN_NAME, props.getSessionIdColumnName());
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_EXCHANGE_ID_COLUMN_NAME, props.getExchangeIdColumnName());
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_ASSISTANT_COLUMN_NAME, props.getAssistantColumnName());
		assertEquals(PgVectorChatMemoryConfig.DEFAULT_USER_COLUMN_NAME, props.getUserColumnName());
		assertTrue(props.isInitializeSchema());
	}

	@Test
	void customValues() {
		var props = new PgVectorChatMemoryProperties();
		props.setSchemaName("custom_schema_name");
		props.setTableName("custom_table_name");
		props.setSessionIdColumnName("custom_session_id_column_name");
		props.setExchangeIdColumnName("custom_exchange_id_column_name");
		props.setAssistantColumnName("custom_assistant_column_name");
		props.setUserColumnName("custom_user_column_name");
		props.setInitializeSchema(false);

		assertEquals("custom_schema_name", props.getSchemaName());
		assertEquals("custom_table_name", props.getTableName());
		assertEquals("custom_session_id_column_name", props.getSessionIdColumnName());
		assertEquals("custom_exchange_id_column_name", props.getExchangeIdColumnName());
		assertEquals("custom_assistant_column_name", props.getAssistantColumnName());
		assertEquals("custom_user_column_name", props.getUserColumnName());
		assertFalse(props.isInitializeSchema());
	}

}
