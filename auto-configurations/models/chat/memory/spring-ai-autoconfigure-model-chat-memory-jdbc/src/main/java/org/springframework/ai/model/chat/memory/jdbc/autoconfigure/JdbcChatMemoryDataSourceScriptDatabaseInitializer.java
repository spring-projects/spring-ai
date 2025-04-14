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

package org.springframework.ai.model.chat.memory.jdbc.autoconfigure;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

class JdbcChatMemoryDataSourceScriptDatabaseInitializer extends DataSourceScriptDatabaseInitializer {

	private static final String SCHEMA_LOCATION = "classpath:org/springframework/ai/chat/memory/jdbc/schema-@@platform@@.sql";

	JdbcChatMemoryDataSourceScriptDatabaseInitializer(DataSource dataSource) {
		super(dataSource, getSettings(dataSource));
	}

	static DatabaseInitializationSettings getSettings(DataSource dataSource) {
		var settings = new DatabaseInitializationSettings();
		settings.setSchemaLocations(resolveSchemaLocations(dataSource));
		settings.setMode(DatabaseInitializationMode.ALWAYS);
		settings.setContinueOnError(true);

		return settings;
	}

	static List<String> resolveSchemaLocations(DataSource dataSource) {
		var platformResolver = new PlatformPlaceholderDatabaseDriverResolver();

		return platformResolver.resolveAll(dataSource, SCHEMA_LOCATION);
	}

}
