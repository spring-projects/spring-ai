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

package org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.init.PropertiesBasedDataSourceScriptDatabaseInitializer;

/**
 * Performs database initialization for the JDBC Chat Memory Repository.
 *
 * @author Mark Pollack
 * @author Yanming Zhou
 * @since 1.0.0
 * @deprecated since 2.1.0, for removal in 3.0.0, in favor of
 * {@code JdbcSessionRepositorySchemaInitializer} in
 * {@code spring-ai-autoconfigure-model-session-jdbc}
 */
@Deprecated(since = "2.1.0", forRemoval = true)
@SuppressWarnings("removal")
class JdbcChatMemoryRepositorySchemaInitializer
		extends PropertiesBasedDataSourceScriptDatabaseInitializer<JdbcChatMemoryRepositoryProperties> {

	JdbcChatMemoryRepositorySchemaInitializer(DataSource dataSource, JdbcChatMemoryRepositoryProperties properties) {
		super(dataSource, properties);
	}

}
