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

package org.springframework.ai.model.chat.memory.repository.neo4j.autoconfigure;

import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Neo4j chat memory.
 *
 * @author Enrico Rampazzo
 */
@ConfigurationProperties(Neo4jChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class Neo4jChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.neo4j";

	private String sessionLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_SESSION_LABEL;

	private String toolCallLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_TOOL_CALL_LABEL;

	private String metadataLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_METADATA_LABEL;

	private String messageLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_MESSAGE_LABEL;

	private String toolResponseLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_TOOL_RESPONSE_LABEL;

	private String mediaLabel = Neo4jChatMemoryRepositoryConfig.DEFAULT_MEDIA_LABEL;

	public String getSessionLabel() {
		return this.sessionLabel;
	}

	public void setSessionLabel(String sessionLabel) {
		this.sessionLabel = sessionLabel;
	}

	public String getToolCallLabel() {
		return this.toolCallLabel;
	}

	public String getMetadataLabel() {
		return this.metadataLabel;
	}

	public String getMessageLabel() {
		return this.messageLabel;
	}

	public String getToolResponseLabel() {
		return this.toolResponseLabel;
	}

	public String getMediaLabel() {
		return this.mediaLabel;
	}

	public void setToolCallLabel(String toolCallLabel) {
		this.toolCallLabel = toolCallLabel;
	}

	public void setMetadataLabel(String metadataLabel) {
		this.metadataLabel = metadataLabel;
	}

	public void setMessageLabel(String messageLabel) {
		this.messageLabel = messageLabel;
	}

	public void setToolResponseLabel(String toolResponseLabel) {
		this.toolResponseLabel = toolResponseLabel;
	}

	public void setMediaLabel(String mediaLabel) {
		this.mediaLabel = mediaLabel;
	}

}
