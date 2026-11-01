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

package org.springframework.ai.chat.memory.repository.jdbc;

/**
 * Dialect for Oracle.
 *
 * @author Xiaotong Fan
 * @author Pablo Silberkasten
 * @author Yanming Zhou
 * @since 1.1.0
 * @deprecated since 2.1.0, for removal in 3.0.0, in favor of
 * {@code JdbcSessionRepository} in {@code spring-ai-session-jdbc}. There is no session
 * dialect for Oracle yet; one is planned before 3.0.0, and until then a dialect of your
 * own implements {@code JdbcSessionRepositoryDialect}.
 */
@Deprecated(since = "2.1.0", forRemoval = true)
@SuppressWarnings("removal")

public class OracleChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

	@Override
	public String getSelectMessagesSql() {
		return "SELECT content, type, \"TIMESTAMP\" FROM SPRING_AI_CHAT_MEMORY WHERE CONVERSATION_ID = ? ORDER BY SEQUENCE_ID";
	}

	@Override
	public String getInsertMessageSql() {
		return "INSERT INTO SPRING_AI_CHAT_MEMORY (CONVERSATION_ID, CONTENT, TYPE, \"TIMESTAMP\", SEQUENCE_ID) VALUES (?, ?, ?, ?, ?)";
	}

}
