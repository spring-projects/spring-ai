/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.memory.pgvector;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.PgVectorChatMemoryConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
class PgVectorChatMemoryPropertiesTests {

	@Test
	void defaultValues() {
		var props = new PgVectorChatMemoryProperties();
		assertThat(props.getSchemaName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_SCHEMA_NAME);
		assertThat(props.getTableName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_TABLE_NAME);
		assertThat(props.getSessionIdColumnName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_SESSION_ID_COLUMN_NAME);
		assertThat(props.getExchangeIdColumnName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_EXCHANGE_ID_COLUMN_NAME);
		assertThat(props.getAssistantColumnName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_ASSISTANT_COLUMN_NAME);
		assertThat(props.getUserColumnName()).isEqualTo(PgVectorChatMemoryConfig.DEFAULT_USER_COLUMN_NAME);
		assertThat(props.isInitializeSchema()).isTrue();
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

		assertThat(props.getSchemaName()).isEqualTo("custom_schema_name");
		assertThat(props.getTableName()).isEqualTo("custom_table_name");
		assertThat(props.getSessionIdColumnName()).isEqualTo("custom_session_id_column_name");
		assertThat(props.getExchangeIdColumnName()).isEqualTo("custom_exchange_id_column_name");
		assertThat(props.getAssistantColumnName()).isEqualTo("custom_assistant_column_name");
		assertThat(props.getUserColumnName()).isEqualTo("custom_user_column_name");
		assertThat(props.isInitializeSchema()).isFalse();
	}

}
