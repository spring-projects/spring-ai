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
package org.springframework.ai.autoconfigure.chat.memory.pgvector;

import org.springframework.ai.autoconfigure.chat.memory.CommonChatMemoryProperties;
import org.springframework.ai.chat.memory.PgVectorChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
@ConfigurationProperties(PgVectorChatMemoryProperties.CONFIG_PREFIX)
public class PgVectorChatMemoryProperties extends CommonChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.pgvector";

	private String schemaName = PgVectorChatMemoryConfig.DEFAULT_SCHEMA_NAME;

	private String tableName = PgVectorChatMemoryConfig.DEFAULT_TABLE_NAME;

	private String sessionIdColumnName = PgVectorChatMemoryConfig.DEFAULT_SESSION_ID_COLUMN_NAME;

	private String exchangeIdColumnName = PgVectorChatMemoryConfig.DEFAULT_EXCHANGE_ID_COLUMN_NAME;

	private String assistantColumnName = PgVectorChatMemoryConfig.DEFAULT_ASSISTANT_COLUMN_NAME;

	private String userColumnName = PgVectorChatMemoryConfig.DEFAULT_USER_COLUMN_NAME;

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getSessionIdColumnName() {
		return sessionIdColumnName;
	}

	public void setSessionIdColumnName(String sessionIdColumnName) {
		this.sessionIdColumnName = sessionIdColumnName;
	}

	public String getExchangeIdColumnName() {
		return exchangeIdColumnName;
	}

	public void setExchangeIdColumnName(String exchangeIdColumnName) {
		this.exchangeIdColumnName = exchangeIdColumnName;
	}

	public String getAssistantColumnName() {
		return assistantColumnName;
	}

	public void setAssistantColumnName(String assistantColumnName) {
		this.assistantColumnName = assistantColumnName;
	}

	public String getUserColumnName() {
		return userColumnName;
	}

	public void setUserColumnName(String userColumnName) {
		this.userColumnName = userColumnName;
	}

}
