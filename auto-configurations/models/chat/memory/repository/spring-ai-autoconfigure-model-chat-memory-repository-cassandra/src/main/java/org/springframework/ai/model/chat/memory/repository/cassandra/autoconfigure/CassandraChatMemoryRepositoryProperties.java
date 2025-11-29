/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.repository.cassandra.autoconfigure;

import java.time.Duration;

import org.springframework.ai.chat.memory.repository.cassandra.CassandraChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Cassandra Chat Memory Repository.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@ConfigurationProperties(CassandraChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class CassandraChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.cassandra";

	/**
	 * Cassandra keyspace name.
	 */
	private String keyspace = CassandraChatMemoryRepositoryConfig.DEFAULT_KEYSPACE_NAME;

	/**
	 * Cassandra table name.
	 */
	private String table = CassandraChatMemoryRepositoryConfig.DEFAULT_TABLE_NAME;

	/**
	 * Cassandra column name for messages.
	 */
	private String messagesColumn = CassandraChatMemoryRepositoryConfig.DEFAULT_MESSAGES_COLUMN_NAME;

	/**
	 * Time to live (TTL) for messages written in Cassandra.
	 */
	private Duration timeToLive;

	/**
	 * Whether to initialize the schema on startup.
	 */
	private boolean initializeSchema = true;

	public String getKeyspace() {
		return this.keyspace;
	}

	public void setKeyspace(String keyspace) {
		this.keyspace = keyspace;
	}

	public String getTable() {
		return this.table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public String getMessagesColumn() {
		return this.messagesColumn;
	}

	public void setMessagesColumn(String messagesColumn) {
		this.messagesColumn = messagesColumn;
	}

	public Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	public boolean isInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

}

