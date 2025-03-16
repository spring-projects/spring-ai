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

package org.springframework.ai.autoconfigure.chat.memory.cassandra;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.autoconfigure.chat.memory.CommonChatMemoryProperties;
import org.springframework.ai.chat.memory.cassandra.CassandraChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Configuration properties for Cassandra chat memory.
 *
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@ConfigurationProperties(CassandraChatMemoryProperties.CONFIG_PREFIX)
public class CassandraChatMemoryProperties extends CommonChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.cassandra";

	private static final Logger logger = LoggerFactory.getLogger(CassandraChatMemoryProperties.class);

	private String keyspace = CassandraChatMemoryConfig.DEFAULT_KEYSPACE_NAME;

	private String table = CassandraChatMemoryConfig.DEFAULT_TABLE_NAME;

	private String assistantColumn = CassandraChatMemoryConfig.DEFAULT_ASSISTANT_COLUMN_NAME;

	private String userColumn = CassandraChatMemoryConfig.DEFAULT_USER_COLUMN_NAME;

	private Duration timeToLive = null;

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

	public String getAssistantColumn() {
		return this.assistantColumn;
	}

	public void setAssistantColumn(String assistantColumn) {
		this.assistantColumn = assistantColumn;
	}

	public String getUserColumn() {
		return this.userColumn;
	}

	public void setUserColumn(String userColumn) {
		this.userColumn = userColumn;
	}

	@Nullable
	public Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

}
