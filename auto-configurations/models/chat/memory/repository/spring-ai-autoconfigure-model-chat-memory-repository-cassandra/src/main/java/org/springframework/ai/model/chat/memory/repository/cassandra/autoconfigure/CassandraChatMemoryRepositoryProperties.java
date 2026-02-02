/*
 * Copyright 2023-2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.repository.cassandra.CassandraChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Cassandra chat memory.
 *
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@ConfigurationProperties(CassandraChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class CassandraChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.cassandra";

	private static final Logger logger = LoggerFactory.getLogger(CassandraChatMemoryRepositoryProperties.class);

	private String keyspace = CassandraChatMemoryRepositoryConfig.DEFAULT_KEYSPACE_NAME;

	private String table = CassandraChatMemoryRepositoryConfig.DEFAULT_TABLE_NAME;

	private String messagesColumn = CassandraChatMemoryRepositoryConfig.DEFAULT_MESSAGES_COLUMN_NAME;

	private boolean initializeSchema = true;

	public boolean isInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	private @Nullable Duration timeToLive = null;

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

	public @Nullable Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(@Nullable Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

}
