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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;

/**
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Yanming Zhou
 * @since 1.0.0
 */
@ConfigurationProperties(JdbcChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class JdbcChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.jdbc";

	private static final String DEFAULT_SCHEMA_LOCATION = "classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql";

	/**
	 * Whether to initialize the schema on startup. Values: embedded, always, never.
	 * Default is embedded.
	 */
	private DatabaseInitializationMode initializeSchema = DatabaseInitializationMode.EMBEDDED;

	/**
	 * Locations of schema (DDL) scripts. Supports comma-separated list. Default is
	 * classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql
	 */
	private String schema = DEFAULT_SCHEMA_LOCATION;

	/**
	 * Platform to use in initialization scripts if the @@platform@@ placeholder is used.
	 * Auto-detected by default.
	 */
	private String platform;

	public DatabaseInitializationMode getInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(DatabaseInitializationMode initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	public String getPlatform() {
		return this.platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getSchema() {
		return this.schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

}
