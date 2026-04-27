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

package org.springframework.ai.chat.memory.repository.elasticsearch;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for Elasticsearch.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
public final class ElasticSearchChatMemoryRepository implements ChatMemoryRepository {

	public static final String CONVERSATION_TS = ElasticSearchChatMemoryRepository.class.getSimpleName()
			+ "_message_timestamp";

	private static final Logger logger = LoggerFactory.getLogger(ElasticSearchChatMemoryRepository.class);

	private final ElasticsearchClient client;

	private final String indexName;

	private ElasticSearchChatMemoryRepository(ElasticSearchChatMemoryRepositoryConfig config) {
		Assert.notNull(config, "config cannot be null");
		this.client = config.getClient();
		this.indexName = config.getIndexName();
	}

	public static ElasticSearchChatMemoryRepository create(ElasticSearchChatMemoryRepositoryConfig config) {
		return new ElasticSearchChatMemoryRepository(config);
	}

	@Override
	public List<String> findConversationIds() {
		logger.info("Finding all conversation IDs from Elasticsearch");

		try {
			SearchResponse<Map> response = this.client.search(
					s -> s.index(this.indexName)
						.size(0)
						.aggregations("unique_conversations", a -> a.terms(t -> t.field("conversationId").size(10000))),
					Map.class);

			return response.aggregations()
				.get("unique_conversations")
				.sterms()
				.buckets()
				.array()
				.stream()
				.map(b -> b.key().stringValue())
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to find conversation IDs", e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		logger.info("Finding messages for conversation: {}", conversationId);

		try {
			SearchResponse<Map> response = this.client.search(s -> s.index(this.indexName)
				.query(q -> q.term(t -> t.field("conversationId").value(conversationId)))
				.sort(sort -> sort.field(f -> f.field("sequenceNumber").order(SortOrder.Asc)))
				.size(10000), Map.class);

			return response.hits()
				.hits()
				.stream()
				.map(Hit::source)
				.map(this::mapToMessage)
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to find messages for conversation: " + conversationId, e);
		}
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
		BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			Map<String, Object> doc = createMessageDocument(conversationId, message, timestamp, i);
			String docId = (String) doc.get("id");

			bulkBuilder.operations(op -> op.index(idx -> idx.index(this.indexName).id(docId).document(doc)));
		}

		bulkRequest(bulkBuilder.build());
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		logger.info("Deleting messages for conversation: {}", conversationId);

		try {
			this.client.deleteByQuery(d -> d.index(this.indexName)
				.query(q -> q.term(t -> t.field("conversationId").value(conversationId))));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to delete messages for conversation: " + conversationId, e);
		}
	}

	private void bulkRequest(BulkRequest bulkRequest) {
		try {
			co.elastic.clients.elasticsearch.core.BulkResponse bulkResponse = this.client.bulk(bulkRequest);
			if (bulkResponse.errors()) {
				throw new IllegalStateException("Bulk operation failed");
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to execute bulk request", e);
		}
	}

	private Map<String, Object> createMessageDocument(String conversationId, Message message, Instant timestamp,
			int sequenceNumber) {
		Map<String, Object> doc = new HashMap<>();
		doc.put("id", UUID.randomUUID().toString());
		doc.put("conversationId", conversationId);
		doc.put("messageType", message.getMessageType().name());
		doc.put("content", message.getText());
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
		String content = (String) doc.get("content");
		String messageTypeStr = (String) doc.get("messageType");
		MessageType messageType = MessageType.valueOf(messageTypeStr);

		// Reconstruct metadata
		Map<String, Object> metadata = new HashMap<>();
		if (doc.containsKey("messageTimestamp")) {
			Long timestampMillis = ((Number) doc.get("messageTimestamp")).longValue();
			metadata.put(CONVERSATION_TS, Instant.ofEpochMilli(timestampMillis));
		}

		// Add any additional metadata that was stored
		@SuppressWarnings("unchecked")
		Map<String, Object> additionalMetadata = (Map<String, Object>) doc.get("metadata");
		if (additionalMetadata != null) {
			metadata.putAll(additionalMetadata);
		}

		return switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content, metadata);
			case USER -> UserMessage.builder().text(content).metadata(metadata).build();
			case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
			case TOOL -> ToolResponseMessage.builder().responses(List.of()).metadata(metadata).build();
			default -> throw new IllegalStateException(String.format("Unknown message type: %s", messageTypeStr));
		};
	}

}
