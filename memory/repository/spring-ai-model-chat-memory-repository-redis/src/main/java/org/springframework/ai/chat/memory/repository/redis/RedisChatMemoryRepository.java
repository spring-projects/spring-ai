package org.springframework.ai.chat.memory.repository.redis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;
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
import org.springframework.util.MimeType;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.RediSearchUtil;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.search.aggr.Reducers;
import redis.clients.jedis.search.querybuilder.QueryBuilders;
import redis.clients.jedis.search.querybuilder.QueryNode;
import redis.clients.jedis.search.querybuilder.Values;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis implementation of {@link ChatMemoryRepository} using Redis (JSON + Query Engine).
 * Stores chat messages as JSON documents and uses the Redis Query Engine for querying.
 *
 * @author Brian Sam-Bodden
 */
public final class RedisChatMemoryRepository implements ChatMemoryRepository, AdvancedRedisChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(RedisChatMemoryRepository.class);

	private static final Gson gson = new Gson();

	private static final Path2 ROOT_PATH = Path2.of("$");

	private final RedisChatMemoryConfig config;

	private final JedisPooled jedis;

	public RedisChatMemoryRepository(RedisChatMemoryConfig config) {
		Assert.notNull(config, "Config must not be null");
		this.config = config;
		this.jedis = config.getJedisClient();

		if (config.isInitializeSchema()) {
			initializeSchema();
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public void add(String conversationId, List<Message> messages) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.notNull(messages, "Messages must not be null");

		if (messages.isEmpty()) {
			return;
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Adding {} messages to conversation: {}", messages.size(), conversationId);
		}

		// Get the next available timestamp for the first message
		long nextTimestamp = getNextTimestampForConversation(conversationId);
		final AtomicLong timestampSequence = new AtomicLong(nextTimestamp);

		try (Pipeline pipeline = jedis.pipelined()) {
			for (Message message : messages) {
				long timestamp = timestampSequence.getAndIncrement();
				String key = createKey(conversationId, timestamp);

				Map<String, Object> documentMap = createMessageDocument(conversationId, message);
				// Ensure the timestamp in the document matches the key timestamp for
				// consistency
				documentMap.put("timestamp", timestamp);

				String json = gson.toJson(documentMap);

				if (logger.isDebugEnabled()) {
					logger.debug("Storing batch message with key: {}, type: {}, content: {}", key,
							message.getMessageType(), message.getText());
				}

				pipeline.jsonSet(key, ROOT_PATH, json);

				if (config.getTimeToLiveSeconds() != -1) {
					pipeline.expire(key, config.getTimeToLiveSeconds());
				}
			}
			pipeline.sync();
		}
	}

	public void add(String conversationId, Message message) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.notNull(message, "Message must not be null");

		if (logger.isDebugEnabled()) {
			logger.debug("Adding message type: {}, content: {} to conversation: {}", message.getMessageType(),
					message.getText(), conversationId);
		}

		// Get the current highest timestamp for this conversation
		long timestamp = getNextTimestampForConversation(conversationId);

		String key = createKey(conversationId, timestamp);
		Map<String, Object> documentMap = createMessageDocument(conversationId, message);

		// Ensure the timestamp in the document matches the key timestamp for consistency
		documentMap.put("timestamp", timestamp);

		String json = gson.toJson(documentMap);

		if (logger.isDebugEnabled()) {
			logger.debug("Storing message with key: {}, JSON: {}", key, json);
		}

		jedis.jsonSet(key, ROOT_PATH, json);

		if (config.getTimeToLiveSeconds() != -1) {
			jedis.expire(key, config.getTimeToLiveSeconds());
		}
	}

	/**
	 * Gets the next available timestamp for a conversation to ensure proper ordering.
	 * Uses Redis Lua script for atomic operations to ensure thread safety when multiple
	 * threads access the same conversation.
	 * @param conversationId the conversation ID
	 * @return the next timestamp to use
	 */
	private long getNextTimestampForConversation(String conversationId) {
		// Create a Redis key specifically for tracking the sequence
		String sequenceKey = String.format("%scounter:%s", config.getKeyPrefix(), escapeKey(conversationId));

		try {
			// Get the current time as base timestamp
			long baseTimestamp = Instant.now().toEpochMilli();
			// Using a Lua script for atomic operation ensures that multiple threads
			// will always get unique and increasing timestamps
			String script = "local exists = redis.call('EXISTS', KEYS[1]) " + "if exists == 0 then "
					+ "  redis.call('SET', KEYS[1], ARGV[1]) " + "  return ARGV[1] " + "end "
					+ "return redis.call('INCR', KEYS[1])";

			// Execute the script atomically
			Object result = jedis.eval(script, java.util.Collections.singletonList(sequenceKey),
					java.util.Collections.singletonList(String.valueOf(baseTimestamp)));

			long nextTimestamp = Long.parseLong(result.toString());

			// Set expiration on the counter key (same as the messages)
			if (config.getTimeToLiveSeconds() != -1) {
				jedis.expire(sequenceKey, config.getTimeToLiveSeconds());
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Generated atomic timestamp {} for conversation {}", nextTimestamp, conversationId);
			}

			return nextTimestamp;
		}

		catch (Exception e) {
			// Log error and fall back to current timestamp with nanoTime for uniqueness
			logger.warn("Error getting atomic timestamp for conversation {}, using fallback: {}", conversationId,
					e.getMessage());
			// Add nanoseconds to ensure uniqueness even in fallback scenario
			return Instant.now().toEpochMilli() * 1000 + (System.nanoTime() % 1000);
		}
	}

	public List<Message> get(String conversationId) {
		return get(conversationId, config.getMaxMessagesPerConversation());
	}

	public List<Message> get(String conversationId, int lastN) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.isTrue(lastN > 0, "LastN must be greater than 0");

		// Use QueryBuilders to create a tag field query for conversation_id
		QueryNode queryNode = QueryBuilders.intersect("conversation_id",
				Values.tags(RediSearchUtil.escape(conversationId)));
		Query query = new Query(queryNode.toString()).setSortBy("timestamp", true).limit(0, lastN);

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		if (logger.isDebugEnabled()) {
			logger.debug("Redis search for conversation {} returned {} results", conversationId,
					result.getDocuments().size());
			result.getDocuments().forEach(doc -> {
				if (doc.get("$") != null) {
					JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
					logger.debug("Document: {}", json);
				}
			});
		}

		List<Message> messages = new ArrayList<>();
		result.getDocuments().forEach(doc -> {
			if (doc.get("$") != null) {
				JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
				if (logger.isDebugEnabled()) {
					logger.debug("Processing JSON document: {}", json);
				}

				String type = json.get("type").getAsString();
				String content = json.get("content").getAsString();

				// Convert metadata from JSON to Map if present
				Map<String, Object> metadata = new HashMap<>();
				if (json.has("metadata") && json.get("metadata").isJsonObject()) {
					JsonObject metadataJson = json.getAsJsonObject("metadata");
					metadataJson.entrySet().forEach(entry -> {
						metadata.put(entry.getKey(), gson.fromJson(entry.getValue(), Object.class));
					});
				}

				if (MessageType.ASSISTANT.toString().equals(type)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Creating AssistantMessage with content: {}", content);
					}

					// Handle tool calls if present
					List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
					if (json.has("toolCalls") && json.get("toolCalls").isJsonArray()) {
						json.getAsJsonArray("toolCalls").forEach(element -> {
							JsonObject toolCallJson = element.getAsJsonObject();
							toolCalls.add(new AssistantMessage.ToolCall(
									toolCallJson.has("id") ? toolCallJson.get("id").getAsString() : "",
									toolCallJson.has("type") ? toolCallJson.get("type").getAsString() : "",
									toolCallJson.has("name") ? toolCallJson.get("name").getAsString() : "",
									toolCallJson.has("arguments") ? toolCallJson.get("arguments").getAsString() : ""));
						});
					}

					// Handle media if present
					List<Media> media = new ArrayList<>();
					if (json.has("media") && json.get("media").isJsonArray()) {
						JsonArray mediaArray = json.getAsJsonArray("media");
						for (JsonElement mediaElement : mediaArray) {
							JsonObject mediaJson = mediaElement.getAsJsonObject();

							// Extract required media properties
							String mediaId = mediaJson.has("id") ? mediaJson.get("id").getAsString() : null;
							String mediaName = mediaJson.has("name") ? mediaJson.get("name").getAsString() : null;
							String mimeTypeString = mediaJson.has("mimeType") ? mediaJson.get("mimeType").getAsString()
									: null;

							if (mimeTypeString != null) {
								MimeType mimeType = MimeType.valueOf(mimeTypeString);
								Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);

								// Set optional properties if present
								if (mediaId != null) {
									mediaBuilder.id(mediaId);
								}

								if (mediaName != null) {
									mediaBuilder.name(mediaName);
								}

								// Handle data based on its type
								if (mediaJson.has("data")) {
									JsonElement dataElement = mediaJson.get("data");
									if (dataElement.isJsonPrimitive() && dataElement.getAsJsonPrimitive().isString()) {
										String dataString = dataElement.getAsString();

										// Check if data is Base64-encoded
										if (mediaJson.has("dataType")
												&& "base64".equals(mediaJson.get("dataType").getAsString())) {
											// Decode Base64 string to byte array
											try {
												byte[] decodedBytes = Base64.getDecoder().decode(dataString);
												mediaBuilder.data(decodedBytes);
											}

											catch (IllegalArgumentException e) {
												logger.warn("Failed to decode Base64 data, storing as string", e);
												mediaBuilder.data(dataString);
											}
										}

										else {
											// Handle URL/URI data
											try {
												mediaBuilder.data(URI.create(dataString));
											}

											catch (IllegalArgumentException e) {
												// Not a valid URI, store as string
												mediaBuilder.data(dataString);
											}
										}
									}

									else if (dataElement.isJsonArray()) {
										// For backward compatibility - handle byte array
										// data stored as JSON array
										JsonArray dataArray = dataElement.getAsJsonArray();
										byte[] byteArray = new byte[dataArray.size()];
										for (int i = 0; i < dataArray.size(); i++) {
											byteArray[i] = dataArray.get(i).getAsByte();
										}
										mediaBuilder.data(byteArray);
									}
								}

								media.add(mediaBuilder.build());
							}
						}
					}

					AssistantMessage assistantMessage = AssistantMessage.builder()
						.content(content)
						.properties(metadata)
						.toolCalls(toolCalls)
						.media(media)
						.build();
					messages.add(assistantMessage);
				}

				else if (MessageType.USER.toString().equals(type)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Creating UserMessage with content: {}", content);
					}

					// Create a UserMessage with the builder to properly set metadata
					List<Media> userMedia = new ArrayList<>();
					if (json.has("media") && json.get("media").isJsonArray()) {
						JsonArray mediaArray = json.getAsJsonArray("media");
						for (JsonElement mediaElement : mediaArray) {
							JsonObject mediaJson = mediaElement.getAsJsonObject();

							// Extract required media properties
							String mediaId = mediaJson.has("id") ? mediaJson.get("id").getAsString() : null;
							String mediaName = mediaJson.has("name") ? mediaJson.get("name").getAsString() : null;
							String mimeTypeString = mediaJson.has("mimeType") ? mediaJson.get("mimeType").getAsString()
									: null;

							if (mimeTypeString != null) {
								MimeType mimeType = MimeType.valueOf(mimeTypeString);
								Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);

								// Set optional properties if present
								if (mediaId != null) {
									mediaBuilder.id(mediaId);
								}

								if (mediaName != null) {
									mediaBuilder.name(mediaName);
								}

								// Handle data based on its type and markers
								if (mediaJson.has("data")) {
									JsonElement dataElement = mediaJson.get("data");
									if (dataElement.isJsonPrimitive() && dataElement.getAsJsonPrimitive().isString()) {
										String dataString = dataElement.getAsString();

										// Check if data is Base64-encoded
										if (mediaJson.has("dataType")
												&& "base64".equals(mediaJson.get("dataType").getAsString())) {
											// Decode Base64 string to byte array
											try {
												byte[] decodedBytes = Base64.getDecoder().decode(dataString);
												mediaBuilder.data(decodedBytes);
											}

											catch (IllegalArgumentException e) {
												logger.warn("Failed to decode Base64 data, storing as string", e);
												mediaBuilder.data(dataString);
											}
										}

										else {
											// Handle URL/URI data
											try {
												mediaBuilder.data(URI.create(dataString));
											}

											catch (IllegalArgumentException e) {
												// Not a valid URI, store as string
												mediaBuilder.data(dataString);
											}
										}
									}

									else if (dataElement.isJsonArray()) {
										// For backward compatibility - handle byte array
										// data stored as JSON array
										JsonArray dataArray = dataElement.getAsJsonArray();
										byte[] byteArray = new byte[dataArray.size()];
										for (int i = 0; i < dataArray.size(); i++) {
											byteArray[i] = dataArray.get(i).getAsByte();
										}
										mediaBuilder.data(byteArray);
									}
								}

								userMedia.add(mediaBuilder.build());
							}
						}
					}
					messages.add(UserMessage.builder().text(content).metadata(metadata).media(userMedia).build());
				}

				else if (MessageType.SYSTEM.toString().equals(type)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Creating SystemMessage with content: {}", content);
					}

					messages.add(SystemMessage.builder().text(content).metadata(metadata).build());
				}

				else if (MessageType.TOOL.toString().equals(type)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Creating ToolResponseMessage with content: {}", content);
					}

					// Extract tool responses
					List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
					if (json.has("toolResponses") && json.get("toolResponses").isJsonArray()) {
						JsonArray responseArray = json.getAsJsonArray("toolResponses");
						for (JsonElement responseElement : responseArray) {
							JsonObject responseJson = responseElement.getAsJsonObject();

							String id = responseJson.has("id") ? responseJson.get("id").getAsString() : "";
							String name = responseJson.has("name") ? responseJson.get("name").getAsString() : "";
							String responseData = responseJson.has("responseData")
									? responseJson.get("responseData").getAsString() : "";

							toolResponses.add(new ToolResponseMessage.ToolResponse(id, name, responseData));
						}
					}

					messages.add(ToolResponseMessage.builder().responses(toolResponses).metadata(metadata).build());
				}
				// Add handling for other message types if needed
				else {
					logger.warn("Unknown message type: {}", type);
				}
			}
		});

		if (logger.isDebugEnabled()) {
			logger.debug("Returning {} messages for conversation {}", messages.size(), conversationId);
			messages.forEach(message -> logger.debug("Message type: {}, content: {}, class: {}",
					message.getMessageType(), message.getText(), message.getClass().getSimpleName()));
		}

		return messages;
	}

	public void clear(String conversationId) {
		Assert.notNull(conversationId, "Conversation ID must not be null");

		// Use QueryBuilders to create a tag field query
		QueryNode queryNode = QueryBuilders.intersect("conversation_id",
				Values.tags(RediSearchUtil.escape(conversationId)));
		Query query = new Query(queryNode.toString());
		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		try (Pipeline pipeline = jedis.pipelined()) {
			result.getDocuments().forEach(doc -> pipeline.del(doc.getId()));
			pipeline.sync();
		}
	}

	private void initializeSchema() {
		try {
			if (!jedis.ftList().contains(config.getIndexName())) {
				List<SchemaField> schemaFields = new ArrayList<>();

				// Basic fields for all messages - using schema field objects
				schemaFields.add(new TextField("$.content").as("content"));
				schemaFields.add(new TextField("$.type").as("type"));
				schemaFields.add(new TagField("$.conversation_id").as("conversation_id"));
				schemaFields.add(new NumericField("$.timestamp").as("timestamp"));

				// Add metadata fields based on user-provided schema or default to text
				if (config.getMetadataFields() != null && !config.getMetadataFields().isEmpty()) {
					// User has provided a metadata schema - use it
					for (Map<String, String> fieldDef : config.getMetadataFields()) {
						String fieldName = fieldDef.get("name");
						String fieldType = fieldDef.getOrDefault("type", "text");
						String jsonPath = "$.metadata." + fieldName;
						String indexedName = "metadata_" + fieldName;

						switch (fieldType.toLowerCase()) {
							case "numeric":
								schemaFields.add(new NumericField(jsonPath).as(indexedName));
								break;
							case "tag":
								schemaFields.add(new TagField(jsonPath).as(indexedName));
								break;
							case "text":
							default:
								schemaFields.add(new TextField(jsonPath).as(indexedName));
								break;
						}
					}
					// When specific metadata fields are defined, we don't add a wildcard
					// metadata field to avoid indexing errors with non-string values
				}

				else {
					// No schema provided - fallback to indexing all metadata as text
					schemaFields.add(new TextField("$.metadata.*").as("metadata"));
				}

				// Create the index with the defined schema
				FTCreateParams indexParams = FTCreateParams.createParams()
					.on(IndexDataType.JSON)
					.prefix(config.getKeyPrefix());

				String response = jedis.ftCreate(config.getIndexName(), indexParams,
						schemaFields.toArray(new SchemaField[0]));

				if (!response.equals("OK")) {
					throw new IllegalStateException("Failed to create index: " + response);
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Created Redis search index '{}' with {} schema fields", config.getIndexName(),
							schemaFields.size());
				}
			}

			else if (logger.isDebugEnabled()) {
				logger.debug("Redis search index '{}' already exists", config.getIndexName());
			}
		}

		catch (Exception e) {
			logger.error("Failed to initialize Redis schema: {}", e.getMessage());
			if (logger.isDebugEnabled()) {
				logger.debug("Error details", e);
			}
			throw new IllegalStateException("Could not initialize Redis schema", e);
		}
	}

	private String createKey(String conversationId, long timestamp) {
		return String.format("%s%s:%d", config.getKeyPrefix(), escapeKey(conversationId), timestamp);
	}

	private Map<String, Object> createMessageDocument(String conversationId, Message message) {
		Map<String, Object> documentMap = new HashMap<>();
		documentMap.put("type", message.getMessageType().toString());
		documentMap.put("content", message.getText());
		documentMap.put("conversation_id", conversationId);
		documentMap.put("timestamp", Instant.now().toEpochMilli());

		// Store metadata/properties
		if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
			documentMap.put("metadata", message.getMetadata());
		}

		// Handle tool calls for AssistantMessage
		if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
			documentMap.put("toolCalls", assistantMessage.getToolCalls());
		}

		// Handle tool responses for ToolResponseMessage
		if (message instanceof ToolResponseMessage toolResponseMessage) {
			documentMap.put("toolResponses", toolResponseMessage.getResponses());
		}

		// Handle media content
		if (message instanceof MediaContent mediaContent && !mediaContent.getMedia().isEmpty()) {
			List<Map<String, Object>> mediaList = new ArrayList<>();

			for (Media media : mediaContent.getMedia()) {
				Map<String, Object> mediaMap = new HashMap<>();

				// Store ID and name if present
				if (media.getId() != null) {
					mediaMap.put("id", media.getId());
				}

				if (media.getName() != null) {
					mediaMap.put("name", media.getName());
				}

				// Store MimeType as string
				if (media.getMimeType() != null) {
					mediaMap.put("mimeType", media.getMimeType().toString());
				}

				// Handle data based on its type
				Object data = media.getData();
				if (data != null) {
					if (data instanceof URI || data instanceof String) {
						// Store URI/URL as string
						mediaMap.put("data", data.toString());
					}

					else if (data instanceof byte[]) {
						// Encode byte array as Base64 string
						mediaMap.put("data", Base64.getEncoder().encodeToString((byte[]) data));
						// Add a marker to indicate this is Base64-encoded
						mediaMap.put("dataType", "base64");
					}

					else {
						// For other types, store as string
						mediaMap.put("data", data.toString());
					}
				}

				mediaList.add(mediaMap);
			}

			documentMap.put("media", mediaList);
		}

		return documentMap;
	}

	private String escapeKey(String key) {
		return key.replace(":", "\\:");
	}

	// ChatMemoryRepository implementation

	/**
	 * Finds all unique conversation IDs using Redis aggregation. This method is optimized
	 * to perform the deduplication on the Redis server side.
	 * @return a list of unique conversation IDs
	 */
	@Override
	public List<String> findConversationIds() {
		// Use Redis aggregation to get distinct conversation_ids
		AggregationBuilder aggregation = new AggregationBuilder("*")
			.groupBy("@conversation_id", Reducers.count().as("count"))
			.limit(0, config.getMaxConversationIds()); // Use configured limit

		AggregationResult result = jedis.ftAggregate(config.getIndexName(), aggregation);

		List<String> conversationIds = new ArrayList<>();
		result.getResults().forEach(row -> {
			String conversationId = (String) row.get("conversation_id");
			if (conversationId != null) {
				conversationIds.add(conversationId);
			}
		});

		if (logger.isDebugEnabled()) {
			logger.debug("Found {} unique conversation IDs using Redis aggregation", conversationIds.size());
			conversationIds.forEach(id -> logger.debug("Conversation ID: {}", id));
		}

		return conversationIds;
	}

	/**
	 * Finds all messages for a given conversation ID. Uses the configured maximum
	 * messages per conversation limit to avoid exceeding Redis limits.
	 * @param conversationId the conversation ID to find messages for
	 * @return a list of messages for the conversation
	 */
	@Override
	public List<Message> findByConversationId(String conversationId) {
		// Reuse existing get method with the configured limit
		return get(conversationId, config.getMaxMessagesPerConversation());
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		// First clear any existing messages for this conversation
		clear(conversationId);

		// Then add all the new messages
		add(conversationId, messages);
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		// Reuse existing clear method
		clear(conversationId);
	}

	// AdvancedChatMemoryRepository implementation

	/**
	 * Gets the index name used by this RedisChatMemory instance.
	 * @return the index name
	 */
	public String getIndexName() {
		return config.getIndexName();
	}

	@Override
	public List<MessageWithConversation> findByContent(String contentPattern, int limit) {
		Assert.notNull(contentPattern, "Content pattern must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		// Use QueryBuilders to create a text field query
		// Note: We don't escape the contentPattern here because Redis full-text search
		// should handle the special characters appropriately in text fields
		QueryNode queryNode = QueryBuilders.intersect("content", Values.value(contentPattern));
		Query query = new Query(queryNode.toString()).setSortBy("timestamp", true).limit(0, limit);

		if (logger.isDebugEnabled()) {
			logger.debug("Searching for messages with content pattern '{}' with limit {}", contentPattern, limit);
		}

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);
		return processSearchResult(result);
	}

	@Override
	public List<MessageWithConversation> findByType(MessageType messageType, int limit) {
		Assert.notNull(messageType, "Message type must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		// Use QueryBuilders to create a text field query
		QueryNode queryNode = QueryBuilders.intersect("type", Values.value(messageType.toString()));
		Query query = new Query(queryNode.toString()).setSortBy("timestamp", true).limit(0, limit);

		if (logger.isDebugEnabled()) {
			logger.debug("Searching for messages of type {} with limit {}", messageType, limit);
		}

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);
		return processSearchResult(result);
	}

	@Override
	public List<MessageWithConversation> findByTimeRange(String conversationId, Instant fromTime, Instant toTime,
			int limit) {
		Assert.notNull(fromTime, "From time must not be null");
		Assert.notNull(toTime, "To time must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");
		Assert.isTrue(!toTime.isBefore(fromTime), "To time must not be before from time");

		// Build query with numeric range for timestamp using the QueryBuilder
		long fromTimeMs = fromTime.toEpochMilli();
		long toTimeMs = toTime.toEpochMilli();

		// Create the numeric range query for timestamp
		QueryNode rangeNode = QueryBuilders.intersect("timestamp", Values.between(fromTimeMs, toTimeMs));

		// If conversationId is provided, add it to the query as a tag filter
		QueryNode finalQuery;
		if (conversationId != null && !conversationId.isEmpty()) {
			QueryNode conversationNode = QueryBuilders.intersect("conversation_id",
					Values.tags(RediSearchUtil.escape(conversationId)));
			finalQuery = QueryBuilders.intersect(rangeNode, conversationNode);
		}

		else {
			finalQuery = rangeNode;
		}

		// Create the query with sorting by timestamp
		Query query = new Query(finalQuery.toString()).setSortBy("timestamp", true).limit(0, limit);

		if (logger.isDebugEnabled()) {
			logger.debug("Searching for messages in time range from {} to {} with limit {}, query: '{}'", fromTime,
					toTime, limit, finalQuery);
		}

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);
		return processSearchResult(result);
	}

	@Override
	public List<MessageWithConversation> findByMetadata(String metadataKey, Object metadataValue, int limit) {
		Assert.notNull(metadataKey, "Metadata key must not be null");
		Assert.notNull(metadataValue, "Metadata value must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		// Check if this metadata field was explicitly defined in the schema
		String indexedFieldName = "metadata_" + metadataKey;
		boolean isFieldIndexed = false;
		String fieldType = "text";

		if (config.getMetadataFields() != null) {
			for (Map<String, String> fieldDef : config.getMetadataFields()) {
				if (metadataKey.equals(fieldDef.get("name"))) {
					isFieldIndexed = true;
					fieldType = fieldDef.getOrDefault("type", "text");
					break;
				}
			}
		}

		QueryNode queryNode;
		if (isFieldIndexed) {
			// Field is explicitly indexed - use proper query based on type
			switch (fieldType.toLowerCase()) {
				case "numeric":
					if (metadataValue instanceof Number) {
						queryNode = QueryBuilders.intersect(indexedFieldName,
								Values.eq(((Number) metadataValue).doubleValue()));
					}

					else {
						// Try to parse as number
						try {
							double numValue = Double.parseDouble(metadataValue.toString());
							queryNode = QueryBuilders.intersect(indexedFieldName, Values.eq(numValue));
						}

						catch (NumberFormatException e) {
							// Fall back to text search in general metadata
							String searchPattern = metadataKey + " " + metadataValue;
							queryNode = QueryBuilders.intersect("metadata", Values.value(searchPattern));
						}
					}
					break;
				case "tag":
					// For tag fields, we don't need to escape the value
					queryNode = QueryBuilders.intersect(indexedFieldName, Values.tags(metadataValue.toString()));
					break;
				case "text":
				default:
					queryNode = QueryBuilders.intersect(indexedFieldName,
							Values.value(RediSearchUtil.escape(metadataValue.toString())));
					break;
			}
		}

		else {
			// Field not explicitly indexed - search in general metadata field
			String searchPattern = metadataKey + " " + metadataValue;
			queryNode = QueryBuilders.intersect("metadata", Values.value(searchPattern));
		}

		Query query = new Query(queryNode.toString()).setSortBy("timestamp", true).limit(0, limit);

		if (logger.isDebugEnabled()) {
			logger.debug("Searching for messages with metadata {}={}, query: '{}', limit: {}", metadataKey,
					metadataValue, queryNode, limit);
		}

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		if (logger.isDebugEnabled()) {
			logger.debug("Search returned {} results", result.getTotalResults());
		}
		return processSearchResult(result);
	}

	@Override
	public List<MessageWithConversation> executeQuery(String query, int limit) {
		Assert.notNull(query, "Query must not be null");
		Assert.isTrue(limit > 0, "Limit must be greater than 0");

		// Create a Query object from the query string
		// The client provides the full Redis Search query syntax
		Query redisQuery = new Query(query).limit(0, limit).setSortBy("timestamp", true); // Default
																							// sorting
																							// by
																							// timestamp
																							// ascending

		if (logger.isDebugEnabled()) {
			logger.debug("Executing custom query '{}' with limit {}", query, limit);
		}

		return executeSearchQuery(redisQuery);
	}

	/**
	 * Processes a search result and converts it to a list of MessageWithConversation
	 * objects.
	 * @param result the search result to process
	 * @return a list of MessageWithConversation objects
	 */
	private List<MessageWithConversation> processSearchResult(SearchResult result) {
		List<MessageWithConversation> messages = new ArrayList<>();

		for (Document doc : result.getDocuments()) {
			if (doc.get("$") != null) {
				// Parse the JSON document
				JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);

				// Extract conversation ID and timestamp
				String conversationId = json.get("conversation_id").getAsString();
				long timestamp = json.get("timestamp").getAsLong();

				// Convert JSON to message
				Message message = convertJsonToMessage(json);

				// Add to result list
				messages.add(new MessageWithConversation(conversationId, message, timestamp));
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Search returned {} messages", messages.size());
		}

		return messages;
	}

	/**
	 * Executes a search query and converts the results to a list of
	 * MessageWithConversation objects. Centralizes the common search execution logic used
	 * by multiple finder methods.
	 * @param query The query to execute
	 * @return A list of MessageWithConversation objects
	 */
	private List<MessageWithConversation> executeSearchQuery(Query query) {
		try {
			// Execute the search
			SearchResult result = jedis.ftSearch(config.getIndexName(), query);
			return processSearchResult(result);
		}

		catch (Exception e) {
			logger.error("Error executing query '{}': {}", query, e.getMessage());
			if (logger.isTraceEnabled()) {
				logger.debug("Error details", e);
			}
			return Collections.emptyList();
		}
	}

	/**
	 * Converts a JSON object to a Message instance. This is a helper method for the
	 * advanced query operations to convert Redis JSON documents back to Message objects.
	 * @param json The JSON object representing a message
	 * @return A Message object of the appropriate type
	 */
	private Message convertJsonToMessage(JsonObject json) {
		String type = json.get("type").getAsString();
		String content = json.get("content").getAsString();

		// Convert metadata from JSON to Map if present
		Map<String, Object> metadata = new HashMap<>();
		if (json.has("metadata") && json.get("metadata").isJsonObject()) {
			JsonObject metadataJson = json.getAsJsonObject("metadata");
			metadataJson.entrySet().forEach(entry -> {
				metadata.put(entry.getKey(), gson.fromJson(entry.getValue(), Object.class));
			});
		}

		if (MessageType.ASSISTANT.toString().equals(type)) {
			// Handle tool calls if present
			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
			if (json.has("toolCalls") && json.get("toolCalls").isJsonArray()) {
				json.getAsJsonArray("toolCalls").forEach(element -> {
					JsonObject toolCallJson = element.getAsJsonObject();
					toolCalls.add(new AssistantMessage.ToolCall(
							toolCallJson.has("id") ? toolCallJson.get("id").getAsString() : "",
							toolCallJson.has("type") ? toolCallJson.get("type").getAsString() : "",
							toolCallJson.has("name") ? toolCallJson.get("name").getAsString() : "",
							toolCallJson.has("arguments") ? toolCallJson.get("arguments").getAsString() : ""));
				});
			}

			// Handle media if present
			List<Media> media = new ArrayList<>();
			if (json.has("media") && json.get("media").isJsonArray()) {
				JsonArray mediaArray = json.getAsJsonArray("media");
				for (JsonElement mediaElement : mediaArray) {
					JsonObject mediaJson = mediaElement.getAsJsonObject();

					// Extract required media properties
					String mediaId = mediaJson.has("id") ? mediaJson.get("id").getAsString() : null;
					String mediaName = mediaJson.has("name") ? mediaJson.get("name").getAsString() : null;
					String mimeTypeString = mediaJson.has("mimeType") ? mediaJson.get("mimeType").getAsString() : null;

					if (mimeTypeString != null) {
						MimeType mimeType = MimeType.valueOf(mimeTypeString);
						Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);

						// Set optional properties if present
						if (mediaId != null) {
							mediaBuilder.id(mediaId);
						}

						if (mediaName != null) {
							mediaBuilder.name(mediaName);
						}

						// Handle data based on its type
						if (mediaJson.has("data")) {
							JsonElement dataElement = mediaJson.get("data");
							if (dataElement.isJsonPrimitive() && dataElement.getAsJsonPrimitive().isString()) {
								String dataString = dataElement.getAsString();

								// Check if data is Base64-encoded
								if (mediaJson.has("dataType")
										&& "base64".equals(mediaJson.get("dataType").getAsString())) {
									// Decode Base64 string to byte array
									try {
										byte[] decodedBytes = Base64.getDecoder().decode(dataString);
										mediaBuilder.data(decodedBytes);
									}

									catch (IllegalArgumentException e) {
										logger.warn("Failed to decode Base64 data, storing as string", e);
										mediaBuilder.data(dataString);
									}
								}

								else {
									// Handle URL/URI data
									try {
										mediaBuilder.data(URI.create(dataString));
									}

									catch (IllegalArgumentException e) {
										// Not a valid URI, store as string
										mediaBuilder.data(dataString);
									}
								}
							}

							else if (dataElement.isJsonArray()) {
								// For backward compatibility - handle byte array data
								// stored as JSON array
								JsonArray dataArray = dataElement.getAsJsonArray();
								byte[] byteArray = new byte[dataArray.size()];
								for (int i = 0; i < dataArray.size(); i++) {
									byteArray[i] = dataArray.get(i).getAsByte();
								}
								mediaBuilder.data(byteArray);
							}
						}

						media.add(mediaBuilder.build());
					}
				}
			}

			return AssistantMessage.builder()
				.content(content)
				.properties(metadata)
				.toolCalls(toolCalls)
				.media(media)
				.build();
		}

		else if (MessageType.USER.toString().equals(type)) {
			// Create a UserMessage with the builder to properly set metadata
			List<Media> userMedia = new ArrayList<>();
			if (json.has("media") && json.get("media").isJsonArray()) {
				JsonArray mediaArray = json.getAsJsonArray("media");
				for (JsonElement mediaElement : mediaArray) {
					JsonObject mediaJson = mediaElement.getAsJsonObject();

					// Extract required media properties
					String mediaId = mediaJson.has("id") ? mediaJson.get("id").getAsString() : null;
					String mediaName = mediaJson.has("name") ? mediaJson.get("name").getAsString() : null;
					String mimeTypeString = mediaJson.has("mimeType") ? mediaJson.get("mimeType").getAsString() : null;

					if (mimeTypeString != null) {
						MimeType mimeType = MimeType.valueOf(mimeTypeString);
						Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);

						// Set optional properties if present
						if (mediaId != null) {
							mediaBuilder.id(mediaId);
						}

						if (mediaName != null) {
							mediaBuilder.name(mediaName);
						}

						// Handle data based on its type and markers
						if (mediaJson.has("data")) {
							JsonElement dataElement = mediaJson.get("data");
							if (dataElement.isJsonPrimitive() && dataElement.getAsJsonPrimitive().isString()) {
								String dataString = dataElement.getAsString();

								// Check if data is Base64-encoded
								if (mediaJson.has("dataType")
										&& "base64".equals(mediaJson.get("dataType").getAsString())) {
									// Decode Base64 string to byte array
									try {
										byte[] decodedBytes = Base64.getDecoder().decode(dataString);
										mediaBuilder.data(decodedBytes);
									}

									catch (IllegalArgumentException e) {
										logger.warn("Failed to decode Base64 data, storing as string", e);
										mediaBuilder.data(dataString);
									}
								}

								else {
									// Handle URL/URI data
									try {
										mediaBuilder.data(URI.create(dataString));
									}

									catch (IllegalArgumentException e) {
										// Not a valid URI, store as string
										mediaBuilder.data(dataString);
									}
								}
							}

							else if (dataElement.isJsonArray()) {
								// For backward compatibility - handle byte array data
								// stored as JSON array
								JsonArray dataArray = dataElement.getAsJsonArray();
								byte[] byteArray = new byte[dataArray.size()];
								for (int i = 0; i < dataArray.size(); i++) {
									byteArray[i] = dataArray.get(i).getAsByte();
								}
								mediaBuilder.data(byteArray);
							}
						}

						userMedia.add(mediaBuilder.build());
					}
				}
			}
			return UserMessage.builder().text(content).metadata(metadata).media(userMedia).build();
		}

		else if (MessageType.SYSTEM.toString().equals(type)) {
			return SystemMessage.builder().text(content).metadata(metadata).build();
		}

		else if (MessageType.TOOL.toString().equals(type)) {
			// Extract tool responses
			List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
			if (json.has("toolResponses") && json.get("toolResponses").isJsonArray()) {
				JsonArray responseArray = json.getAsJsonArray("toolResponses");
				for (JsonElement responseElement : responseArray) {
					JsonObject responseJson = responseElement.getAsJsonObject();

					String id = responseJson.has("id") ? responseJson.get("id").getAsString() : "";
					String name = responseJson.has("name") ? responseJson.get("name").getAsString() : "";
					String responseData = responseJson.has("responseData")
							? responseJson.get("responseData").getAsString() : "";

					toolResponses.add(new ToolResponseMessage.ToolResponse(id, name, responseData));
				}
			}

			return ToolResponseMessage.builder().responses(toolResponses).metadata(metadata).build();
		}

		// For unknown message types, return a generic UserMessage
		logger.warn("Unknown message type: {}, returning generic UserMessage", type);
		return UserMessage.builder().text(content).metadata(metadata).build();
	}

	/**
	 * Inner static builder class for constructing instances of {@link RedisChatMemory}.
	 */
	public static class Builder {

		private @Nullable JedisPooled jedisClient;

		private String indexName = RedisChatMemoryConfig.DEFAULT_INDEX_NAME;

		private String keyPrefix = RedisChatMemoryConfig.DEFAULT_KEY_PREFIX;

		private boolean initializeSchema = true;

		private long timeToLiveSeconds = -1;

		private int maxConversationIds = 10;

		private int maxMessagesPerConversation = 100;

		private List<Map<String, String>> metadataFields = Collections.emptyList();

		/**
		 * Sets the JedisPooled client.
		 * @param jedisClient the JedisPooled client to use
		 * @return this builder
		 */
		public Builder jedisClient(final JedisPooled jedisClient) {
			this.jedisClient = jedisClient;
			return this;
		}

		/**
		 * Sets the index name.
		 * @param indexName the index name to use
		 * @return this builder
		 */
		public Builder indexName(final String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets the key prefix.
		 * @param keyPrefix the key prefix to use
		 * @return this builder
		 */
		public Builder keyPrefix(final String keyPrefix) {
			this.keyPrefix = keyPrefix;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema whether to initialize the schema
		 * @return this builder
		 */
		public Builder initializeSchema(final boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the time to live in seconds for messages stored in Redis.
		 * @param timeToLiveSeconds the time to live in seconds (use -1 for no expiration)
		 * @return this builder
		 */
		public Builder ttlSeconds(final long timeToLiveSeconds) {
			this.timeToLiveSeconds = timeToLiveSeconds;
			return this;
		}

		/**
		 * Sets the time to live duration for messages stored in Redis.
		 * @param timeToLive the time to live duration (null for no expiration)
		 * @return this builder
		 */
		public Builder timeToLive(final Duration timeToLive) {
			if (timeToLive != null) {
				this.timeToLiveSeconds = timeToLive.getSeconds();
			}

			else {
				this.timeToLiveSeconds = -1;
			}
			return this;
		}

		/**
		 * Sets the maximum number of conversation IDs to return.
		 * @param maxConversationIds the maximum number of conversation IDs
		 * @return this builder
		 */
		public Builder maxConversationIds(final int maxConversationIds) {
			this.maxConversationIds = maxConversationIds;
			return this;
		}

		/**
		 * Sets the maximum number of messages per conversation to return.
		 * @param maxMessagesPerConversation the maximum number of messages per
		 * conversation
		 * @return this builder
		 */
		public Builder maxMessagesPerConversation(final int maxMessagesPerConversation) {
			this.maxMessagesPerConversation = maxMessagesPerConversation;
			return this;
		}

		/**
		 * Sets the metadata field definitions for proper indexing. Format is compatible
		 * with RedisVL schema format.
		 * @param metadataFields list of field definitions
		 * @return this builder
		 */
		public Builder metadataFields(List<Map<String, String>> metadataFields) {
			this.metadataFields = metadataFields;
			return this;
		}

		/**
		 * Builds and returns an instance of {@link RedisChatMemoryRepository}.
		 * @return a new {@link RedisChatMemoryRepository} instance
		 */
		public RedisChatMemoryRepository build() {
			Assert.notNull(this.jedisClient, "JedisClient must not be null");

			RedisChatMemoryConfig config = new RedisChatMemoryConfig.Builder().jedisClient(this.jedisClient)
				.indexName(this.indexName)
				.keyPrefix(this.keyPrefix)
				.initializeSchema(this.initializeSchema)
				.timeToLive(Duration.ofSeconds(this.timeToLiveSeconds))
				.maxConversationIds(this.maxConversationIds)
				.maxMessagesPerConversation(this.maxMessagesPerConversation)
				.metadataFields(this.metadataFields)
				.build();

			return new RedisChatMemoryRepository(config);
		}

	}

}
