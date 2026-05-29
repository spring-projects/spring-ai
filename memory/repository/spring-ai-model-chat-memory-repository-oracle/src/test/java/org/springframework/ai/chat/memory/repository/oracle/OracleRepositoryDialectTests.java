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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link OracleRepositoryDialect}.
 */
class OracleRepositoryDialectTests {

	@Test
	void sqlUsesConfiguredTableName() {
		var dialect = new OracleRepositoryDialect("SPRING_AI_CHAT_MEMORY_CUSTOM");

		assertThat(dialect.getSelectMessagesSql()).contains("SPRING_AI_CHAT_MEMORY_CUSTOM");
		assertThat(dialect.getInsertMessageSql()).contains("SPRING_AI_CHAT_MEMORY_CUSTOM");
		assertThat(dialect.getSelectConversationIdsSql()).contains("SPRING_AI_CHAT_MEMORY_CUSTOM");
		assertThat(dialect.getDeleteMessagesSql()).contains("SPRING_AI_CHAT_MEMORY_CUSTOM");
	}

	@Test
	void acceptsSchemaQualifiedTableName() {
		var dialect = new OracleRepositoryDialect("APP.SPRING_AI_CHAT_MEMORY");

		assertThat(dialect.getSelectMessagesSql()).contains("APP.SPRING_AI_CHAT_MEMORY");
	}

	@Test
	void rejectsBlankTableName() {
		assertThatIllegalArgumentException().isThrownBy(() -> new OracleRepositoryDialect(" "));
	}

	@Test
	void rejectsInvalidTableName() {
		assertThatIllegalArgumentException().isThrownBy(() -> new OracleRepositoryDialect("bad-name"));
	}

}
