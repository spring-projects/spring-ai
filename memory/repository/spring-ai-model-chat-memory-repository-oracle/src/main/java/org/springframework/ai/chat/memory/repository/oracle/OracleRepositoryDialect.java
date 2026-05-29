/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.memory.repository.oracle;

import java.util.regex.Pattern;

import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.util.Assert;

/**
 * Oracle SQL dialect for chat memory repository.
 *
 * @since 2.0.0
 */
final class OracleRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

	private static final Pattern TABLE_NAME_PATTERN = Pattern
		.compile("^[A-Za-z][A-Za-z0-9_$#]*(\\.[A-Za-z][A-Za-z0-9_$#]*)?$");

	private final String tableName;

	OracleRepositoryDialect(final String tableName) {
		Assert.hasText(tableName, "tableName cannot be null or empty");
		Assert.isTrue(TABLE_NAME_PATTERN.matcher(tableName).matches(),
				"tableName must be an Oracle identifier or schema-qualified identifier");
		this.tableName = tableName;
	}

	@Override
	public String getSelectMessagesSql() {
		return "SELECT content, type FROM " + this.tableName + " WHERE CONVERSATION_ID = ? ORDER BY \"TIMESTAMP\"";
	}

	@Override
	public String getInsertMessageSql() {
		return "INSERT INTO " + this.tableName + " (CONVERSATION_ID, CONTENT, TYPE, \"TIMESTAMP\") VALUES (?, ?, ?, ?)";
	}

	@Override
	public String getSelectConversationIdsSql() {
		return "SELECT DISTINCT conversation_id FROM " + this.tableName;
	}

	@Override
	public String getDeleteMessagesSql() {
		return "DELETE FROM " + this.tableName + " WHERE CONVERSATION_ID = ?";
	}

}
