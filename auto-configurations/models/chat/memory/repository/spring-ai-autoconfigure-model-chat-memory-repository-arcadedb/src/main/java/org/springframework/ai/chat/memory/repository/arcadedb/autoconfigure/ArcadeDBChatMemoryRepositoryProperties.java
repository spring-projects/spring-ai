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

package org.springframework.ai.chat.memory.repository.arcadedb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ArcadeDB ChatMemoryRepository.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
@ConfigurationProperties(ArcadeDBChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class ArcadeDBChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.arcadedb";

	private String databasePath;

	private String sessionTypeName = "Session";

	private String messageTypeName = "ChatMessage";

	private String edgeTypeName = "HAS_MESSAGE";

	public String getDatabasePath() {
		return databasePath;
	}

	public void setDatabasePath(String databasePath) {
		this.databasePath = databasePath;
	}

	public String getSessionTypeName() {
		return sessionTypeName;
	}

	public void setSessionTypeName(String sessionTypeName) {
		this.sessionTypeName = sessionTypeName;
	}

	public String getMessageTypeName() {
		return messageTypeName;
	}

	public void setMessageTypeName(String messageTypeName) {
		this.messageTypeName = messageTypeName;
	}

	public String getEdgeTypeName() {
		return edgeTypeName;
	}

	public void setEdgeTypeName(String edgeTypeName) {
		this.edgeTypeName = edgeTypeName;
	}

}
