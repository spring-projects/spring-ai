package org.springframework.ai.chat.memory.jdbc.aot.hint;

import javax.sql.DataSource;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * A {@link RuntimeHintsRegistrar} for JDBC Chat Memory hints
 *
 * @author Jonathan Leijendekker
 */
class JdbcChatMemoryRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.reflection()
			.registerType(DataSource.class, (hint) -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

		hints.resources()
			.registerPattern("org/springframework/ai/chat/memory/jdbc/schema-mariadb.sql")
			.registerPattern("org/springframework/ai/chat/memory/jdbc/schema-postgresql.sql");
	}

}
