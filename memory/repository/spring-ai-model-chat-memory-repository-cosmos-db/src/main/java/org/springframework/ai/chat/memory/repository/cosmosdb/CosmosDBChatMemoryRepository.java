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

package org.springframework.ai.chat.memory.repository.cosmosdb;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosBulkOperations;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedFlux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for Azure Cosmos DB.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
public final class CosmosDBChatMemoryRepository implements ChatMemoryRepository {

	public static final String CONVERSATION_TS = CosmosDBChatMemoryRepository.class.getSimpleName()
			+ "_message_timestamp";

	private static final Logger logger = LoggerFactory.getLogger(CosmosDBChatMemoryRepository.class);

	private final CosmosAsyncContainer container;

	private CosmosDBChatMemoryRepository(CosmosDBChatMemoryRepositoryConfig config) {
		Assert.notNull(config, "config cannot be null");
		this.container = config.getContainer();
	}

	public static CosmosDBChatMemoryRepository create(CosmosDBChatMemoryRepositoryConfig config) {
		return new CosmosDBChatMemoryRepository(config);
	}

	@Override
	public List<String> findConversationIds() {
		logger.info("Finding all conversation IDs from Cosmos DB");

		String query = "SELECT DISTINCT c.conversationId FROM c";
		SqlQuerySpec querySpec = new SqlQuerySpec(query);

		CosmosPagedFlux<Object> results = this.container.queryItems(querySpec, new CosmosQueryRequestOptions(),
				Object.class);

		List<Object> conversationDocs = results.byPage()
			.flatMapIterable(FeedResponse::getResults)
			.collectList()
			.block();

		if (conversationDocs == null) {
			return Collections.emptyList();
		}

		return conversationDocs.stream()
			.filter(Map.class::isInstance)
			.map(doc -> (Map<?, ?>) doc)
			.map(doc -> (String) doc.get("conversationId"))
			.distinct()
			.collect(Collectors.toList());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		logger.info("Finding messages for conversation: {}", conversationId);

		String query = "SELECT * FROM c WHERE c.conversationId = @conversationId ORDER BY c._ts ASC";
		SqlParameter param = new SqlParameter("@conversationId", conversationId);
		SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(param));

		CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
			.setPartitionKey(new PartitionKey(conversationId));

		CosmosPagedFlux<Object> results = this.container.queryItems(querySpec, options, Object.class);

		List<Object> messageDocs = results.byPage().flatMapIterable(FeedResponse::getResults).collectList().block();

		if (messageDocs == null) {
			return Collections.emptyList();
		}

		@SuppressWarnings("unchecked")
		List<Message> messages = messageDocs.stream()
			.filter(Map.class::isInstance)
			.map(doc -> (Map<String, Object>) doc)
			.map(this::mapToMessage)
			.collect(Collectors.toList());

		return messages;
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		logger.info("Saving {} messages for conversation: {}", messages.size(), conversationId);

		// First delete existing messages for this conversation
		deleteByConversationId(conversationId);

		// Then save the new messages
		Instant timestamp = Instant.now();

		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			Map<String, Object> doc = createMessageDocument(conversationId, message, timestamp, i);

			this.container.createItem(doc, new PartitionKey(conversationId), new CosmosItemRequestOptions()).block();
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		logger.info("Deleting messages for conversation: {}", conversationId);

		String query = "SELECT c.id FROM c WHERE c.conversationId = @conversationId";
		SqlParameter param = new SqlParameter("@conversationId", conversationId);
		SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(param));

		CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
			.setPartitionKey(new PartitionKey(conversationId));

		CosmosPagedFlux<Object> results = this.container.queryItems(querySpec, options, Object.class);

		List<Object> items = results.byPage().flatMapIterable(FeedResponse::getResults).collectList().block();

		if (items == null || items.isEmpty()) {
			return;
		}

		@SuppressWarnings("unchecked")
		List<CosmosItemOperation> operations = items.stream()
			.filter(Map.class::isInstance)
			.map(item -> (Map<String, Object>) item)
			.map(item -> CosmosBulkOperations.getDeleteItemOperation((String) item.get("id"),
					new PartitionKey(conversationId)))
			.collect(Collectors.toList());

		this.container.executeBulkOperations(Flux.fromIterable(operations)).collectList().block();
	}

	private Map<String, Object> createMessageDocument(String conversationId, Message message, Instant timestamp,
			int sequenceNumber) {
		Map<String, Object> doc = new HashMap<>();
		doc.put("id", UUID.randomUUID().toString());
		doc.put("conversationId", conversationId);
		doc.put("messageType", message.getMessageType().name());
		if (message.getText() != null) {
			doc.put("content", message.getText());
		}
		doc.put("sequenceNumber", sequenceNumber);

		// Add timestamp from metadata or use provided timestamp
		Instant messageTimestamp = (Instant) message.getMetadata().get(CONVERSATION_TS);
		if (messageTimestamp == null) {
			messageTimestamp = timestamp;
			message.getMetadata().put(CONVERSATION_TS, messageTimestamp);
		}
		doc.put("messageTimestamp", messageTimestamp.toEpochMilli());

		// Store any additional metadata
		Map<String, Object> filteredMetadata = message.getMetadata()
			.entrySet()
			.stream()
			.filter(entry -> !CONVERSATION_TS.equals(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		if (!filteredMetadata.isEmpty()) {
			doc.put("metadata", filteredMetadata);
		}

		return doc;
	}

	private Message mapToMessage(Map<String, Object> doc) {
		String content = (String) Objects.requireNonNull(doc.get("content"));
		String messageTypeStr = (String) Objects.requireNonNull(doc.get("messageType"));
		MessageType messageType = MessageType.valueOf(messageTypeStr);

		// Reconstruct metadata
		Map<String, Object> metadata = new HashMap<>();
		if (doc.containsKey("messageTimestamp")) {
			long timestampMillis = ((Number) doc.get("messageTimestamp")).longValue();
			metadata.put(CONVERSATION_TS, Instant.ofEpochMilli(timestampMillis));
		}

		// Add any additional metadata that was stored
		@SuppressWarnings("unchecked")
		Map<String, Object> additionalMetadata = (Map<String, Object>) doc.get("metadata");
		if (additionalMetadata != null) {
			metadata.putAll(additionalMetadata);
		}

		return switch (messageType) {
			case ASSISTANT -> AssistantMessage.builder().content(content).properties(metadata).build();
			case USER -> UserMessage.builder().text(content).metadata(metadata).build();
			case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
			case TOOL -> ToolResponseMessage.builder().responses(List.of()).metadata(metadata).build();
			default -> throw new IllegalStateException(String.format("Unknown message type: %s", messageTypeStr));
		};
	}

}
