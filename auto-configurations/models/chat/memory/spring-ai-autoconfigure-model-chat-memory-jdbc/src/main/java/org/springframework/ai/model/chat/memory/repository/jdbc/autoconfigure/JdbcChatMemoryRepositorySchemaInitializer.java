/*
 * Copyright 2024-2025 the original author or authors.
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

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.util.StringUtils;

/**
 * Performs database initialization for the JDBC Chat Memory Repository.
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
class JdbcChatMemoryRepositorySchemaInitializer extends DataSourceScriptDatabaseInitializer {

	private static final String DEFAULT_SCHEMA_LOCATION = "classpath:org/springframework/ai/chat/memory/jdbc/schema-@@platform@@.sql";

	JdbcChatMemoryRepositorySchemaInitializer(DataSource dataSource, JdbcChatMemoryRepositoryProperties properties) {
		super(dataSource, getSettings(dataSource, properties));
	}

	static DatabaseInitializationSettings getSettings(DataSource dataSource,
			JdbcChatMemoryRepositoryProperties properties) {
		var settings = new DatabaseInitializationSettings();

		// Determine schema locations
		String schemaProp = properties.getSchema();
		List<String> schemaLocations;
		PlatformPlaceholderDatabaseDriverResolver resolver = new PlatformPlaceholderDatabaseDriverResolver();
		try {
			String url = dataSource.getConnection().getMetaData().getURL().toLowerCase();
			if (url.contains("hsqldb")) {
				schemaLocations = List.of("classpath:org/springframework/ai/chat/memory/jdbc/schema-hsqldb.sql");
			}
			else if (StringUtils.hasText(schemaProp)) {
				schemaLocations = resolver.resolveAll(dataSource, schemaProp);
			}
			else {
				schemaLocations = resolver.resolveAll(dataSource, DEFAULT_SCHEMA_LOCATION);
			}
		}
		catch (Exception e) {
			// fallback to default
			if (StringUtils.hasText(schemaProp)) {
				schemaLocations = resolver.resolveAll(dataSource, schemaProp);
			}
			else {
				schemaLocations = resolver.resolveAll(dataSource, DEFAULT_SCHEMA_LOCATION);
			}
		}
		settings.setSchemaLocations(schemaLocations);

		// Determine initialization mode
		JdbcChatMemoryRepositoryProperties.DatabaseInitializationMode init = properties.getInitializeSchema();
		DatabaseInitializationMode mode;
		if (JdbcChatMemoryRepositoryProperties.DatabaseInitializationMode.ALWAYS.equals(init)) {
			mode = DatabaseInitializationMode.ALWAYS;
		}
		else if (JdbcChatMemoryRepositoryProperties.DatabaseInitializationMode.NEVER.equals(init)) {
			mode = DatabaseInitializationMode.NEVER;
		}
		else {
			// embedded or default
			mode = DatabaseInitializationMode.EMBEDDED;
		}
		settings.setMode(mode);
		settings.setContinueOnError(true);
		return settings;
	}

}
