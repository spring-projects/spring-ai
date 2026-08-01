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

package org.springframework.ai.chat.memory.repository.neo4j;

import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;

import org.springframework.util.Assert;

/**
 * Configuration for the Neo4j Chat Memory store.
 *
 * @author Enrico Rampazzo
 * @author Soby Chacko
 */
public final class Neo4jChatMemoryRepositoryConfig {

	// todo – make configurable

	public static final String DEFAULT_SESSION_LABEL = "Session";

	public static final String DEFAULT_TOOL_CALL_LABEL = "ToolCall";

	public static final String DEFAULT_METADATA_LABEL = "Metadata";

	public static final String DEFAULT_MESSAGE_LABEL = "Message";

	public static final String DEFAULT_TOOL_RESPONSE_LABEL = "ToolResponse";

	public static final String DEFAULT_MEDIA_LABEL = "Media";

	private static final Log logger = LogFactory.getLog(Neo4jChatMemoryRepositoryConfig.class);

	private static final Pattern SAFE_LABEL = Pattern.compile("[\\p{Alpha}_][\\p{Alnum}_]*");

	private final Driver driver;

	private final String sessionLabel;

	private final String toolCallLabel;

	private final String metadataLabel;

	private final String messageLabel;

	private final String toolResponseLabel;

	private final String mediaLabel;

	public String getSessionLabel() {
		return this.sessionLabel;
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

	public Driver getDriver() {
		return this.driver;
	}

	private Neo4jChatMemoryRepositoryConfig(Builder builder) {
		Assert.state(builder.driver != null, "driver must not be null");
		this.driver = builder.driver;
		this.sessionLabel = builder.sessionLabel;
		this.mediaLabel = builder.mediaLabel;
		this.messageLabel = builder.messageLabel;
		this.toolCallLabel = builder.toolCallLabel;
		this.metadataLabel = builder.metadataLabel;
		this.toolResponseLabel = builder.toolResponseLabel;
		validateLabels();
		ensureIndexes();
	}

	private void validateLabels() {
		for (String label : new String[] { this.sessionLabel, this.messageLabel, this.metadataLabel, this.mediaLabel,
				this.toolCallLabel, this.toolResponseLabel }) {
			if (!SAFE_LABEL.matcher(label).matches()) {
				throw new IllegalArgumentException("Invalid Neo4j node label: '" + label
						+ "'. Labels must start with a letter or underscore and contain only letters, digits, or underscores.");
			}
		}
	}

	/**
	 * Ensures that indexes exist on conversationId for Session nodes and index for
	 * Message nodes. This improves query performance for lookups and ordering.
	 */
	private void ensureIndexes() {
		try (var session = this.driver.session()) {
			// Index for conversationId on Session nodes
			String sessionIndexCypher = String.format(
					"CREATE INDEX session_conversation_id_index IF NOT EXISTS FOR (n:%s) ON (n.conversationId)",
					this.sessionLabel);
			// Index for index on Message nodes
			String messageIndexCypher = String
				.format("CREATE INDEX message_index_index IF NOT EXISTS FOR (n:%s) ON (n.index)", this.messageLabel);
			session.run(sessionIndexCypher);
			session.run(messageIndexCypher);
			logger.info("Ensured Neo4j indexes for conversationId and message index.");
		}
		catch (Exception e) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failed to ensure Neo4j indexes for chat memory: " + e.getMessage());
			}
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable Driver driver;

		private String sessionLabel = DEFAULT_SESSION_LABEL;

		private String toolCallLabel = DEFAULT_TOOL_CALL_LABEL;

		private String metadataLabel = DEFAULT_METADATA_LABEL;

		private String messageLabel = DEFAULT_MESSAGE_LABEL;

		private String toolResponseLabel = DEFAULT_TOOL_RESPONSE_LABEL;

		private String mediaLabel = DEFAULT_MEDIA_LABEL;

		private Builder() {
		}

		public String getSessionLabel() {
			return this.sessionLabel;
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

		public @Nullable Driver getDriver() {
			return this.driver;
		}

		public Builder withDriver(Driver driver) {
			this.driver = driver;
			return this;
		}

		public Neo4jChatMemoryRepositoryConfig build() {
			return new Neo4jChatMemoryRepositoryConfig(this);
		}

	}

}
