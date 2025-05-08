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
class JdbcChatMemoryRepositoryRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.reflection()
			.registerType(DataSource.class, hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

		hints.resources().registerPattern("org/springframework/ai/chat/memory/jdbc/schema-*.sql");
	}

}
