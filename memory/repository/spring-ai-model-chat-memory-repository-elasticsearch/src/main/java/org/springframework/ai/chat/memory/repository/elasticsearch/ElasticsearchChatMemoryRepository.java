/*
 * Copyright 2026-2026 the original author or authors.
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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.Version;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.Assert;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of {@link ChatMemoryRepository} with advanced query
 * capabilities. Stores chat messages as documents in an Elasticsearch index.
 *
 * <p>
 * Client creation follows the same pattern as
 * {@code org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore}:
 * accepts a {@link Rest5Client} and internally builds an {@link ElasticsearchClient} via
 * {@link Rest5ClientTransport} + {@link Jackson3JsonpMapper}.
 * </p>
 *
 * @author Laura Trotta
 * @since 2.0.0
 */
public final class ElasticsearchChatMemoryRepository
		implements ChatMemoryRepository, AdvancedElasticsearchChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(ElasticsearchChatMemoryRepository.class);

	private static final String CONVERSATION_FIELD = "conversation_id";

	private static final String TYPE_FIELD = "type";

	private static final String CONTENT_FIELD = "content";

	private static final String TIMESTAMP_FIELD = "timestamp";

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
		.changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
		.build();

	private final ElasticsearchClient elasticsearchClient;

	private final String indexName;

	private final boolean initializeSchema;

	private final int maxResults;

	public ElasticsearchChatMemoryRepository(ElasticsearchChatMemoryConfig config) {
		Assert.notNull(config, "Config must not be null");
		this.indexName = config.getIndexName();
		this.initializeSchema = config.isInitializeSchema();
		this.maxResults = config.getMaxResults();
		String version = Version.VERSION == null ? "Unknown" : Version.VERSION.toString();
		this.elasticsearchClient = new ElasticsearchClient(
				new Rest5ClientTransport(config.getEsRestClient(), new Jackson3JsonpMapper(this.jsonMapper)))
			.withTransportOptions(t -> t.addHeader("user-agent", "spring-ai elastic-java/" + version));

		if (initializeSchema) {
			initializeSchema();
		}
	}

	public static Builder builder(Rest5Client restClient) {
		return new Builder(restClient);
	}

	@Override
	public List<String> findConversationIds() {
		try {
			SearchResponse<Void> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.size(0)
				.aggregations("conversation_ids",
						a -> a.terms(t -> t.field(CONVERSATION_FIELD).size(this.maxResults))));

			Aggregate aggregation = response.aggregations().get("conversation_ids");
			if (aggregation == null) {
				return Collections.emptyList();
			}
			List<String> ids = aggregation.sterms()
				.buckets()
				.array()
				.stream()
				.map(bucket -> bucket.key().stringValue())
				.collect(Collectors.toList());

			if (logger.isDebugEnabled()) {
				logger.debug("Found {} unique conversation IDs", ids.size());
			}
			return ids;
		}
		catch (IOException e) {
			logger.error("Failed to find conversation IDs: {}", e.getMessage());
			throw new RuntimeException("Failed to find conversation IDs", e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		return getMessages(conversationId);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.notNull(messages, "Messages must not be null");

		deleteByConversationId(conversationId);
		if (!messages.isEmpty()) {
			addMessages(conversationId, messages);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		try {
			this.elasticsearchClient.deleteByQuery(d -> d.index(this.indexName)
				.query(q -> q.term(t -> t.field(CONVERSATION_FIELD).value(conversationId))));
			this.elasticsearchClient.indices().refresh(r -> r.index(this.indexName));
		}
		catch (IOException e) {
			logger.error("Failed to delete messages for conversation {}: {}", conversationId, e.getMessage());
			throw new RuntimeException("Failed to delete messages for conversation " + conversationId, e);
		}
	}

	// advanced interface method overrides from here

	@Override
	public List<MessageWithConversation> findByContent(String contentPattern, int limit) {
		Assert.notNull(contentPattern, "Content pattern must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.query(q -> q.match(m -> m.field(CONTENT_FIELD).query(contentPattern)))
				.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
				.size(limit), MessageDocument.class);
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to find messages by content: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<MessageWithConversation> findByType(MessageType messageType, int limit) {
		Assert.notNull(messageType, "Message type must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.query(q -> q.term(t -> t.field(TYPE_FIELD).value(messageType.toString())))
				.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
				.size(limit), MessageDocument.class);
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to find messages by type: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<MessageWithConversation> findByTimeRange(String conversationId, Instant fromTime, Instant toTime,
			int limit) {
		Assert.notNull(fromTime, "From time must not be null");
		Assert.notNull(toTime, "To time must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");
		Assert.isTrue(!toTime.isBefore(fromTime), "To time must not be before from time");

		long fromTimeMs = fromTime.toEpochMilli();
		long toTimeMs = toTime.toEpochMilli();

		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> {
				s.index(this.indexName)
					.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
					.size(limit);

				if (conversationId != null && !conversationId.isEmpty()) {
					s.query(q -> q.bool(b -> b
						.must(m -> m.range(r -> r
							.number(n -> n.field(TIMESTAMP_FIELD).gte((double) fromTimeMs).lte((double) toTimeMs))))
						.must(m -> m.term(t -> t.field(CONVERSATION_FIELD).value(conversationId)))));
				}
				else {
					s.query(q -> q.range(r -> r
						.number(n -> n.field(TIMESTAMP_FIELD).gte((double) fromTimeMs).lte((double) toTimeMs))));
				}
				return s;
			}, MessageDocument.class);
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to find messages by time range: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<MessageWithConversation> findByMetadata(String metadataKey, Object metadataValue, int limit) {
		Assert.notNull(metadataKey, "Metadata key must not be null");
		Assert.notNull(metadataValue, "Metadata value must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		String field = "metadata." + metadataKey;

		try {
			SearchResponse<MessageDocument> response;
			if (metadataValue instanceof Number number) {
				response = this.elasticsearchClient.search(s -> s.index(this.indexName)
					.query(q -> q.term(t -> t.field(field).value(number.doubleValue())))
					.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
					.size(limit), MessageDocument.class);
			}
			else {
				String stringValue = metadataValue.toString();
				response = this.elasticsearchClient.search(s -> s.index(this.indexName)
					.query(q -> q.term(t -> t.field(field).value(stringValue)))
					.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
					.size(limit), MessageDocument.class);
			}
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to find messages by metadata: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<MessageWithConversation> executeQuery(String query, int limit) {
		Assert.notNull(query, "Query must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.query(q -> q.withJson(new StringReader(query)))
				.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
				.size(limit), MessageDocument.class);
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to execute custom query: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<MessageWithConversation> executeQuery(Query query, int limit) {
		Assert.notNull(query, "Query must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.query(query)
				.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
				.size(limit), MessageDocument.class);
			return processSearchResponse(response);
		}
		catch (IOException e) {
			logger.error("Failed to execute custom query: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	public String getIndexName() {
		return this.indexName;
	}

	private void initializeSchema() {
		try {
			boolean exists = this.elasticsearchClient.indices().exists(e -> e.index(this.indexName)).value();
			if (!exists) {
				this.elasticsearchClient.indices()
					.create(c -> c.index(this.indexName)
						.mappings(m -> m.properties(CONVERSATION_FIELD, p -> p.keyword(k -> k))
							.properties(TYPE_FIELD, p -> p.keyword(k -> k))
							.properties(CONTENT_FIELD, p -> p.text(t -> t))
							.properties(TIMESTAMP_FIELD, p -> p.long_(l -> l))
							.properties("media",
									p -> p.object(o -> o.properties("contentString", pr -> pr.text(k -> k))
										.properties("name", pr -> pr.keyword(k -> k))
										.properties("id", pr -> pr.keyword(k -> k))
										.properties("mimeType", pr -> pr.keyword(k -> k))
										.properties("contentBytes", pr -> pr.binary(k -> k))))
							.properties("toolResponses",
									p -> p.object(o -> o.properties("name", pr -> pr.keyword(k -> k))
										.properties("responseData", pr -> pr.text(k -> k))
										.properties("id", pr -> pr.keyword(k -> k))))
							.properties("toolCalls",
									p -> p.object(o -> o.properties("name", pr -> pr.keyword(k -> k))
										.properties("arguments", pr -> pr.text(k -> k))
										.properties("id", pr -> pr.keyword(k -> k))
										.properties("type", pr -> pr.keyword(k -> k))))
							.properties("metadata", p -> p.object(o -> o))));
				if (logger.isDebugEnabled()) {
					logger.debug("Created Elasticsearch index '{}'", this.indexName);
				}
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Elasticsearch index '{}' already exists", this.indexName);
			}
		}
		catch (IOException e) {
			logger.error("Failed to initialize Elasticsearch schema: {}", e.getMessage());
			throw new IllegalStateException("Could not initialize Elasticsearch schema", e);
		}
	}

	private List<Message> getMessages(String conversationId) {
		try {
			SearchResponse<MessageDocument> response = this.elasticsearchClient.search(s -> s.index(this.indexName)
				.query(q -> q.term(t -> t.field(CONVERSATION_FIELD).value(conversationId)))
				.sort(so -> so.field(f -> f.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
				.size(this.maxResults), MessageDocument.class);

			List<Message> messages = new ArrayList<>();
			for (Hit<MessageDocument> hit : response.hits().hits()) {
				MessageDocument source = hit.source();
				if (source != null) {
					messages.add(convertMessageDocumentToSpecificMessage(source));
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Returning {} messages for conversation {}", messages.size(), conversationId);
			}
			return messages;
		}
		catch (IOException e) {
			logger.error("Failed to get messages for conversation {}: {}", conversationId, e.getMessage());
			throw new RuntimeException("Failed to get messages for conversation " + conversationId, e);
		}
	}

	private void addMessages(String conversationId, List<Message> messages) {
		long baseTimestamp = Instant.now().toEpochMilli();
		AtomicLong timestampSequence = new AtomicLong(baseTimestamp);

		BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
		for (Message message : messages) {
			long timestamp = timestampSequence.getAndIncrement();
			MessageDocument doc = createMessageDocument(conversationId, message, timestamp);
			bulkBuilder.operations(op -> op.index(idx -> idx.index(this.indexName).document(doc)));
		}

		try {
			BulkResponse bulkResponse = this.elasticsearchClient.bulk(bulkBuilder.build());
			if (bulkResponse.errors()) {
				logger.error("Errors occurred during bulk indexing for conversation {}", conversationId);
				bulkResponse.items().stream().filter(item -> item.error() != null).forEach(item -> {
					logger.error("Bulk item error: {}", item.error().reason());
				});
			}
			this.elasticsearchClient.indices().refresh(r -> r.index(this.indexName));
		}
		catch (IOException e) {
			logger.error("Failed to add messages for conversation {}: {}", conversationId, e.getMessage());
			throw new RuntimeException("Failed to add messages for conversation " + conversationId, e);
		}
	}

	private List<MessageWithConversation> processSearchResponse(SearchResponse<MessageDocument> response) {
		List<MessageWithConversation> results = new ArrayList<>();
		for (Hit<MessageDocument> hit : response.hits().hits()) {
			MessageDocument source = hit.source();
			if (source != null) {
				String conversationId = source.conversation_id();
				long timestamp = source.timestamp();
				Message message = convertMessageDocumentToSpecificMessage(source);
				results.add(new MessageWithConversation(conversationId, message, timestamp));
			}
		}
		return results;
	}

	private MessageDocument createMessageDocument(String conversationId, Message message, long timestamp) {

		List<AssistantMessage.ToolCall> toolCalls = null;
		List<ToolResponseMessage.ToolResponse> toolResponses = null;
		List<MediaForDocument> media = null;

		if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
			toolCalls = assistantMessage.getToolCalls();
		}
		if (message instanceof ToolResponseMessage toolResponseMessage) {
			toolResponses = toolResponseMessage.getResponses();
		}
		if (message instanceof MediaContent mediaContent && !mediaContent.getMedia().isEmpty()) {
			media = mediaContent.getMedia().stream().map(MediaForDocument::fromMedia).collect(Collectors.toList());
		}

		String content = message.getText() != null ? message.getText() : "";
		return new MessageDocument(conversationId, message.getMessageType().toString(), content, timestamp,
				message.getMetadata(), toolCalls, toolResponses, media);
	}

	private Message convertMessageDocumentToSpecificMessage(MessageDocument source) {
		List<Media> media = Optional.ofNullable(source.media())
			.map(x -> x.stream().map(MediaForDocument::toMedia).toList())
			.orElse(List.of());
		List<ToolResponseMessage.ToolResponse> toolResponses = Optional.ofNullable(source.toolResponses())
			.orElse(List.of());
		return switch (MessageType.valueOf(source.type())) {
			case ASSISTANT -> {
				List<AssistantMessage.ToolCall> toolCalls = Optional.ofNullable(source.toolCalls()).orElse(List.of());
				yield AssistantMessage.builder()
					.content(source.content())
					.properties(source.metadata())
					.toolCalls(toolCalls)
					.media(media)
					.build();
			}
			case USER -> UserMessage.builder().text(source.content()).metadata(source.metadata()).media(media).build();
			case SYSTEM -> SystemMessage.builder().text(source.content()).metadata(source.metadata()).build();
			case TOOL -> ToolResponseMessage.builder().responses(toolResponses).metadata(source.metadata()).build();
		};
	}

	/**
	 * Builder for constructing {@link ElasticsearchChatMemoryRepository} instances.
	 */
	public static class Builder {

		private final Rest5Client restClient;

		private String indexName = ElasticsearchChatMemoryConfig.DEFAULT_INDEX_NAME;

		private boolean initializeSchema = true;

		private int maxResults = ElasticsearchChatMemoryConfig.DEFAULT_MAX_RESULTS;

		private Builder(Rest5Client restClient) {
			Assert.notNull(restClient, "Rest5Client must not be null");
			this.restClient = restClient;
		}

		/**
		 * Sets the Elasticsearch index name.
		 * @param indexName the index name to use
		 * @return this builder
		 */
		public Builder indexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets whether to initialize the index schema on startup.
		 * @param initializeSchema whether to initialize the schema
		 * @return this builder
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the maximum number of results to return.
		 * @param maxResults the maximum number of results.
		 * @return this builder
		 */
		public Builder maxResults(int maxResults) {
			this.maxResults = maxResults;
			return this;
		}

		/**
		 * Builds and returns a new {@link ElasticsearchChatMemoryRepository} instance.
		 * @return a new ElasticsearchChatMemoryRepository
		 */
		public ElasticsearchChatMemoryRepository build() {
			ElasticsearchChatMemoryConfig config = ElasticsearchChatMemoryConfig.builder(restClient)
				.indexName(indexName)
				.initializeSchema(initializeSchema)
				.maxResults(maxResults)
				.build();

			return new ElasticsearchChatMemoryRepository(config);
		}

	}

}
