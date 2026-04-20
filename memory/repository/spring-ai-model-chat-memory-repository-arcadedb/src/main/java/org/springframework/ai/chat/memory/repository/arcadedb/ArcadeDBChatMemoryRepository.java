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

package org.springframework.ai.chat.memory.repository.arcadedb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.schema.VertexType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * ArcadeDB implementation of {@link ChatMemoryRepository} using the native
 * graph model.
 *
 * <p>
 * Stores conversation history as a graph:
 * <pre>
 * (Session vertex: conversationId) --HAS_MESSAGE edge--&gt;
 *     (ChatMessage vertex: type, content, metadata)
 * </pre>
 *
 * <p>
 * ArcadeDB edges are naturally LIFO-ordered, so traversing
 * {@code out('HAS_MESSAGE')} returns messages in reverse insertion order. We
 * reverse this to restore chronological order.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
public final class ArcadeDBChatMemoryRepository
		implements ChatMemoryRepository, AutoCloseable {

	private static final Logger logger = LoggerFactory
			.getLogger(ArcadeDBChatMemoryRepository.class);

	static final String DEFAULT_SESSION_TYPE = "Session";

	static final String DEFAULT_MESSAGE_TYPE = "ChatMessage";

	static final String DEFAULT_EDGE_TYPE = "HAS_MESSAGE";

	static final String PROP_CONVERSATION_ID = "conversationId";

	static final String PROP_MESSAGE_TYPE = "messageType";

	static final String PROP_CONTENT = "content";

	static final String PROP_TOOL_CALLS = "toolCalls";

	static final String PROP_METADATA = "metadata";

	private final Database database;

	private final boolean ownsDatabase;

	private final String sessionTypeName;

	private final String messageTypeName;

	private final String edgeTypeName;

	private final ObjectMapper objectMapper;

	private ArcadeDBChatMemoryRepository(Builder builder) {
		this.sessionTypeName = builder.sessionTypeName;
		this.messageTypeName = builder.messageTypeName;
		this.edgeTypeName = builder.edgeTypeName;
		this.objectMapper = new ObjectMapper();

		if (builder.database != null) {
			this.database = builder.database;
			this.ownsDatabase = false;
		}
		else {
			if (builder.databasePath == null
					|| builder.databasePath.isBlank()) {
				throw new IllegalArgumentException(
						"Either database or databasePath must be provided");
			}
			DatabaseFactory factory = new DatabaseFactory(
					builder.databasePath);
			this.database = factory.exists() ? factory.open()
					: factory.create();
			this.ownsDatabase = true;
		}

		initSchema();
	}

	private void initSchema() {
		database.transaction(() -> {
			Schema schema = database.getSchema();

			VertexType sessionType = schema.existsType(sessionTypeName)
					? (VertexType) schema.getType(sessionTypeName)
					: schema.createVertexType(sessionTypeName, 1);
			if (!sessionType.existsProperty(PROP_CONVERSATION_ID)) {
				sessionType.createProperty(PROP_CONVERSATION_ID,
						Type.STRING);
			}
			if (sessionType.getPolymorphicIndexByProperties(
					PROP_CONVERSATION_ID) == null) {
				schema.createTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true,
						sessionTypeName, PROP_CONVERSATION_ID);
			}

			VertexType messageType = schema.existsType(messageTypeName)
					? (VertexType) schema.getType(messageTypeName)
					: schema.createVertexType(messageTypeName, 1);
			if (!messageType.existsProperty(PROP_MESSAGE_TYPE)) {
				messageType.createProperty(PROP_MESSAGE_TYPE, Type.STRING);
			}
			if (!messageType.existsProperty(PROP_CONTENT)) {
				messageType.createProperty(PROP_CONTENT, Type.STRING);
			}
			if (!messageType.existsProperty(PROP_TOOL_CALLS)) {
				messageType.createProperty(PROP_TOOL_CALLS, Type.STRING);
			}
			if (!messageType.existsProperty(PROP_METADATA)) {
				messageType.createProperty(PROP_METADATA, Type.STRING);
			}

			if (!schema.existsType(edgeTypeName)) {
				schema.createEdgeType(edgeTypeName);
			}
		});
	}

	@Override
	public List<String> findConversationIds() {
		List<String> ids = new ArrayList<>();
		try (ResultSet rs = database.query("sql",
				"SELECT FROM `" + sessionTypeName + "`")) {
			while (rs.hasNext()) {
				Result result = rs.next();
				result.getVertex().ifPresent(v -> {
					String cid = v.getString(PROP_CONVERSATION_ID);
					if (cid != null) {
						ids.add(cid);
					}
				});
			}
		}
		return ids;
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Vertex session = findSessionVertex(conversationId);
		if (session == null) {
			return List.of();
		}

		List<Message> messages = new ArrayList<>();
		for (Vertex messageVertex : session.getVertices(
				Vertex.DIRECTION.OUT, edgeTypeName)) {
			Message message = vertexToMessage(messageVertex);
			if (message != null) {
				messages.add(message);
			}
		}

		Collections.reverse(messages);
		return messages;
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		deleteByConversationId(conversationId);

		database.transaction(() -> {
			MutableVertex session = database.newVertex(sessionTypeName);
			session.set(PROP_CONVERSATION_ID, conversationId);
			session.save();
			Vertex savedSession = session.asVertex();

			for (Message message : messages) {
				MutableVertex msgVertex = database
						.newVertex(messageTypeName);
				msgVertex.set(PROP_MESSAGE_TYPE,
						message.getMessageType().name());
				msgVertex.set(PROP_CONTENT, message.getText());

				Map<String, Object> metadata = message.getMetadata();
				if (metadata != null && !metadata.isEmpty()) {
					try {
						msgVertex.set(PROP_METADATA,
								objectMapper.writeValueAsString(metadata));
					}
					catch (JsonProcessingException ex) {
						logger.warn(
								"Failed to serialize message metadata: {}",
								ex.getMessage());
					}
				}

				if (message instanceof AssistantMessage assistantMsg
						&& assistantMsg.getToolCalls() != null
						&& !assistantMsg.getToolCalls().isEmpty()) {
					try {
						msgVertex.set(PROP_TOOL_CALLS, objectMapper
								.writeValueAsString(
										assistantMsg.getToolCalls()));
					}
					catch (JsonProcessingException ex) {
						logger.warn(
								"Failed to serialize tool calls: {}",
								ex.getMessage());
					}
				}

				msgVertex.save();
				savedSession.newEdge(edgeTypeName, msgVertex, true,
						new Object[0]);
			}
		});
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		database.transaction(() -> {
			Vertex session = findSessionVertex(conversationId);
			if (session == null) {
				return;
			}

			List<Vertex> messagesToDelete = new ArrayList<>();
			for (Vertex messageVertex : session.getVertices(
					Vertex.DIRECTION.OUT, edgeTypeName)) {
				messagesToDelete.add(messageVertex);
			}
			for (Vertex msg : messagesToDelete) {
				msg.delete();
			}

			session.delete();
		});
	}

	@Override
	public void close() {
		if (ownsDatabase && database != null && database.isOpen()) {
			database.close();
		}
	}

	/**
	 * Returns the underlying ArcadeDB {@link Database} instance.
	 * @return the database
	 */
	public Database getNativeClient() {
		return this.database;
	}

	private Vertex findSessionVertex(String conversationId) {
		try (ResultSet rs = database.query("sql",
				"SELECT FROM `" + sessionTypeName + "` WHERE "
						+ PROP_CONVERSATION_ID + " = ?",
				conversationId)) {
			if (rs.hasNext()) {
				Result result = rs.next();
				return result.getVertex().orElse(null);
			}
		}
		return null;
	}

	private Message vertexToMessage(Vertex vertex) {
		String typeStr = vertex.getString(PROP_MESSAGE_TYPE);
		String content = vertex.has(PROP_CONTENT)
				? vertex.getString(PROP_CONTENT) : "";

		Map<String, Object> metadata = Map.of();
		if (vertex.has(PROP_METADATA)) {
			String metaJson = vertex.getString(PROP_METADATA);
			if (metaJson != null && !metaJson.isEmpty()) {
				try {
					metadata = objectMapper.readValue(metaJson,
							new TypeReference<Map<String, Object>>() {
							});
				}
				catch (JsonProcessingException ex) {
					logger.warn(
							"Failed to deserialize message metadata: {}",
							ex.getMessage());
				}
			}
		}

		try {
			MessageType messageType = MessageType.valueOf(typeStr);
			return switch (messageType) {
				case USER -> new UserMessage(content);
				case ASSISTANT -> new AssistantMessage(content, metadata);
				case SYSTEM -> new SystemMessage(content);
				case TOOL -> new ToolResponseMessage(
						List.of(new ToolResponseMessage.ToolResponse(null,
								null, content)),
						metadata);
			};
		}
		catch (Exception ex) {
			logger.warn("Failed to deserialize message of type {}: {}",
					typeStr, ex.getMessage());
			return null;
		}
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @return a new Builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ArcadeDBChatMemoryRepository}.
	 *
	 * @since 2.0.0
	 */
	public static class Builder {

		private String databasePath;

		private Database database;

		private String sessionTypeName = DEFAULT_SESSION_TYPE;

		private String messageTypeName = DEFAULT_MESSAGE_TYPE;

		private String edgeTypeName = DEFAULT_EDGE_TYPE;

		/**
		 * Set the path for the embedded ArcadeDB database directory.
		 * @param databasePath the database path
		 * @return this builder
		 */
		public Builder databasePath(String databasePath) {
			this.databasePath = databasePath;
			return this;
		}

		/**
		 * Use an existing ArcadeDB {@link Database} instance.
		 * @param database the database instance
		 * @return this builder
		 */
		public Builder database(Database database) {
			this.database = database;
			return this;
		}

		/**
		 * Set the vertex type name for conversation sessions.
		 * @param sessionTypeName the type name (default: "Session")
		 * @return this builder
		 */
		public Builder sessionTypeName(String sessionTypeName) {
			this.sessionTypeName = sessionTypeName;
			return this;
		}

		/**
		 * Set the vertex type name for chat messages.
		 * @param messageTypeName the type name (default: "ChatMessage")
		 * @return this builder
		 */
		public Builder messageTypeName(String messageTypeName) {
			this.messageTypeName = messageTypeName;
			return this;
		}

		/**
		 * Set the edge type connecting sessions to messages.
		 * @param edgeTypeName the edge type (default: "HAS_MESSAGE")
		 * @return this builder
		 */
		public Builder edgeTypeName(String edgeTypeName) {
			this.edgeTypeName = edgeTypeName;
			return this;
		}

		/**
		 * Build the {@link ArcadeDBChatMemoryRepository}.
		 * @return a new repository instance
		 */
		public ArcadeDBChatMemoryRepository build() {
			return new ArcadeDBChatMemoryRepository(this);
		}

	}

}
