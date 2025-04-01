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

package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the Neo4j Chat Memory store.
 *
 * @author Enrico Rampazzo
 */
public final class Neo4jChatMemoryConfig {

	// todo – make configurable

	public static final String DEFAULT_SESSION_LABEL = "Session";

	public static final String DEFAULT_TOOL_CALL_LABEL = "ToolCall";

	public static final String DEFAULT_METADATA_LABEL = "Metadata";

	public static final String DEFAULT_MESSAGE_LABEL = "Message";

	public static final String DEFAULT_TOOL_RESPONSE_LABEL = "ToolResponse";

	public static final String DEFAULT_MEDIA_LABEL = "Media";

	private static final Logger logger = LoggerFactory.getLogger(Neo4jChatMemoryConfig.class);

	private final Driver driver;

	private final String sessionLabel;

	private final String toolCallLabel;

	private final String metadataLabel;

	private final String messageLabel;

	private final String toolResponseLabel;

	private final String mediaLabel;

	public String getSessionLabel() {
		return sessionLabel;
	}

	public String getToolCallLabel() {
		return toolCallLabel;
	}

	public String getMetadataLabel() {
		return metadataLabel;
	}

	public String getMessageLabel() {
		return messageLabel;
	}

	public String getToolResponseLabel() {
		return toolResponseLabel;
	}

	public String getMediaLabel() {
		return mediaLabel;
	}

	public Driver getDriver() {
		return driver;
	}

	private Neo4jChatMemoryConfig(Builder builder) {
		this.driver = builder.driver;
		this.sessionLabel = builder.sessionLabel;
		this.mediaLabel = builder.mediaLabel;
		this.messageLabel = builder.messageLabel;
		this.toolCallLabel = builder.toolCallLabel;
		this.metadataLabel = builder.metadataLabel;
		this.toolResponseLabel = builder.toolResponseLabel;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Driver driver;

		private String sessionLabel = DEFAULT_SESSION_LABEL;

		private String toolCallLabel = DEFAULT_TOOL_CALL_LABEL;

		private String metadataLabel = DEFAULT_METADATA_LABEL;

		private String messageLabel = DEFAULT_MESSAGE_LABEL;

		private String toolResponseLabel = DEFAULT_TOOL_RESPONSE_LABEL;

		private String mediaLabel = DEFAULT_MEDIA_LABEL;

		private Builder() {
		}

		public String getSessionLabel() {
			return sessionLabel;
		}

		public String getToolCallLabel() {
			return toolCallLabel;
		}

		public String getMetadataLabel() {
			return metadataLabel;
		}

		public String getMessageLabel() {
			return messageLabel;
		}

		public String getToolResponseLabel() {
			return toolResponseLabel;
		}

		public String getMediaLabel() {
			return mediaLabel;
		}

		public Builder withSessionLabel(String sessionLabel) {
			this.sessionLabel = sessionLabel;
			return this;
		}

		public Builder withToolCallLabel(String toolCallLabel) {
			this.toolCallLabel = toolCallLabel;
			return this;
		}

		public Builder withMetadataLabel(String metadataLabel) {
			this.metadataLabel = metadataLabel;
			return this;
		}

		public Builder withMessageLabel(String messageLabel) {
			this.messageLabel = messageLabel;
			return this;
		}

		public Builder withToolResponseLabel(String toolResponseLabel) {
			this.toolResponseLabel = toolResponseLabel;
			return this;
		}

		public Builder withMediaLabel(String mediaLabel) {
			this.mediaLabel = mediaLabel;
			return this;
		}

		public Driver getDriver() {
			return driver;
		}

		public Builder withDriver(Driver driver) {
			this.driver = driver;
			return this;
		}

		public Neo4jChatMemoryConfig build() {
			return new Neo4jChatMemoryConfig(this);
		}

	}

}
