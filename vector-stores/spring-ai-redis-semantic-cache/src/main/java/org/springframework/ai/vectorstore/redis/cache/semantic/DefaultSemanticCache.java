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

package org.springframework.ai.vectorstore.redis.cache.semantic;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import org.springframework.ai.chat.cache.semantic.SemanticCache;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

/**
 * Default implementation of SemanticCache using Redis as the backing store. This
 * implementation uses vector similarity search to find cached responses for semantically
 * similar queries.
 *
 * @author Brian Sam-Bodden
 */
public final class DefaultSemanticCache implements SemanticCache {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSemanticCache.class);

	// Default configuration constants
	private static final String DEFAULT_INDEX_NAME = "semantic-cache-index";

	private static final String DEFAULT_PREFIX = "semantic-cache:";

	private static final Integer DEFAULT_BATCH_SIZE = 100;

	private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.8;

	// Core components
	private final VectorStore vectorStore;

	private final EmbeddingModel embeddingModel;

	private final double similarityThreshold;

	private final boolean useDistanceThreshold;

	private final Gson gson;

	private final String prefix;

	private final String indexName;

	/**
	 * Private constructor enforcing builder pattern usage.
	 */
	private DefaultSemanticCache(VectorStore vectorStore, EmbeddingModel embeddingModel, double similarityThreshold,
			String indexName, String prefix, boolean useDistanceThreshold) {
		this.vectorStore = vectorStore;
		this.embeddingModel = embeddingModel;
		this.similarityThreshold = similarityThreshold;
		this.useDistanceThreshold = useDistanceThreshold;
		this.prefix = prefix;
		this.indexName = indexName;
		this.gson = createGson();
	}

	/**
	 * Creates a customized Gson instance with type adapters for special types.
	 */
	private Gson createGson() {
		return new GsonBuilder() //
			.registerTypeAdapter(Duration.class, new DurationAdapter()) //
			.registerTypeAdapter(ChatResponse.class, new ChatResponseAdapter()) //
			.create();
	}

	@Override
	public VectorStore getStore() {
		return this.vectorStore;
	}

	@Override
	public void set(String query, ChatResponse response) {
		// Convert response to JSON for storage
		String responseJson = this.gson.toJson(response);
		String responseText = response.getResult().getOutput().getText();

		// Create metadata map for the document
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("response", responseJson);
		metadata.put("response_text", responseText);

		// Create document with query as text (for embedding) and response in metadata
		Document document = Document.builder().text(query).metadata(metadata).build();

		// Check for and remove any existing similar documents using optimized search
		// where possible
		List<Document> existing;

		if (this.vectorStore instanceof org.springframework.ai.vectorstore.redis.RedisVectorStore redisVectorStore) {
			// Use the optimized VECTOR_RANGE query which handles thresholding at the DB
			// level
			existing = redisVectorStore.searchByRange(query, this.similarityThreshold);

			if (logger.isDebugEnabled()) {
				logger.debug(
						"Using RedisVectorStore's native VECTOR_RANGE query to find similar documents for replacement");
			}
		}
		else {
			// Fallback to standard similarity search if not using RedisVectorStore
			existing = this.vectorStore.similaritySearch(
					SearchRequest.builder().query(query).topK(1).similarityThreshold(this.similarityThreshold).build());
		}

		// If similar document exists, delete it first
		if (!existing.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing similar document with id={} and score={}", existing.get(0).getId(),
						existing.get(0).getScore());
			}
			this.vectorStore.delete(List.of(existing.get(0).getId()));
		}

		// Add new document to vector store
		this.vectorStore.add(List.of(document));
	}

	@Override
	public void set(String query, ChatResponse response, Duration ttl) {
		// Generate a unique ID for the document
		String docId = UUID.randomUUID().toString();

		// Convert response to JSON
		String responseJson = this.gson.toJson(response);
		String responseText = response.getResult().getOutput().getText();

		// Create metadata
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("response", responseJson);
		metadata.put("response_text", responseText);

		// Create document with generated ID
		Document document = Document.builder().id(docId).text(query).metadata(metadata).build();

		// Check for and remove any existing similar documents using optimized search
		// where possible
		List<Document> existing;

		if (this.vectorStore instanceof RedisVectorStore redisVectorStore) {
			// Use the optimized VECTOR_RANGE query which handles thresholding at the DB
			// level
			existing = redisVectorStore.searchByRange(query, this.similarityThreshold);

			if (logger.isDebugEnabled()) {
				logger.debug(
						"Using RedisVectorStore's native VECTOR_RANGE query to find similar documents for replacement (TTL version)");
			}
		}
		else {
			// Fallback to standard similarity search if not using RedisVectorStore
			existing = this.vectorStore.similaritySearch(
					SearchRequest.builder().query(query).topK(1).similarityThreshold(this.similarityThreshold).build());
		}

		// If similar document exists, delete it first
		if (!existing.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing similar document with id={} and score={}", existing.get(0).getId(),
						existing.get(0).getScore());
			}
			this.vectorStore.delete(List.of(existing.get(0).getId()));
		}

		// Add document to vector store
		this.vectorStore.add(List.of(document));

		// Get access to Redis client and set TTL
		if (this.vectorStore instanceof RedisVectorStore redisStore) {
			String key = this.prefix + docId;
			redisStore.getJedis().expire(key, ttl.getSeconds());
		}
	}

	@Override
	public Optional<ChatResponse> get(String query) {
		// Use RedisVectorStore's searchByRange to utilize the VECTOR_RANGE command
		// for direct threshold filtering at the database level
		List<Document> similar;

		// Convert distance threshold to similarity threshold if needed
		double effectiveThreshold = this.similarityThreshold;
		if (this.useDistanceThreshold) {
			// RedisVL uses distance thresholds: distance <= threshold
			// Spring AI uses similarity thresholds: similarity >= threshold
			// For COSINE: distance = 2 - 2 * similarity, so similarity = 1 - distance/2
			effectiveThreshold = 1 - (this.similarityThreshold / 2);
			if (logger.isDebugEnabled()) {
				logger.debug("Converting distance threshold {} to similarity threshold {}", this.similarityThreshold,
						effectiveThreshold);
			}
		}

		if (this.vectorStore instanceof org.springframework.ai.vectorstore.redis.RedisVectorStore redisVectorStore) {
			// Use the optimized VECTOR_RANGE query which handles thresholding at the DB
			// level
			similar = redisVectorStore.searchByRange(query, effectiveThreshold);

			if (logger.isDebugEnabled()) {
				logger.debug("Using RedisVectorStore's native VECTOR_RANGE query with threshold {}",
						effectiveThreshold);
			}
		}
		else {
			// Fallback to standard similarity search if not using RedisVectorStore
			if (logger.isDebugEnabled()) {
				logger.debug("Falling back to standard similarity search (vectorStore is not RedisVectorStore)");
			}
			similar = this.vectorStore.similaritySearch(
					SearchRequest.builder().query(query).topK(5).similarityThreshold(effectiveThreshold).build());
		}

		if (similar.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("No documents met the similarity threshold criteria");
			}
			return Optional.empty();
		}

		// Log results for debugging
		if (logger.isDebugEnabled()) {
			logger.debug("Query: '{}', found {} matches with similarity >= {}", query, similar.size(),
					this.similarityThreshold);
			for (Document doc : similar) {
				logger.debug("  - Document: id={}, score={}, raw_vector_score={}", doc.getId(), doc.getScore(),
						doc.getMetadata().getOrDefault("vector_score", "N/A"));
			}
		}

		// Get the most similar document (already filtered by threshold at DB level)
		Document mostSimilar = similar.get(0);

		if (logger.isDebugEnabled()) {
			logger.debug("Using most similar document: id={}, score={}", mostSimilar.getId(), mostSimilar.getScore());
		}

		// Get stored response JSON from metadata
		String responseJson = (String) mostSimilar.getMetadata().get("response");
		if (responseJson == null) {
			return Optional.empty();
		}

		// Attempt to parse stored response
		try {
			ChatResponse response = this.gson.fromJson(responseJson, ChatResponse.class);
			return Optional.of(response);
		}
		catch (JsonParseException e) {
			return Optional.empty();
		}
	}

	@Override
	public void clear() {
		Optional<JedisPooled> nativeClient = this.vectorStore.getNativeClient();
		if (nativeClient.isPresent()) {
			JedisPooled jedis = nativeClient.get();

			// Delete documents in batches to avoid memory issues
			boolean moreRecords = true;
			while (moreRecords) {
				Query query = new Query("*");
				query.limit(0, DEFAULT_BATCH_SIZE); // Reasonable batch size
				query.setNoContent();

				SearchResult searchResult = jedis.ftSearch(this.indexName, query);

				if (searchResult.getTotalResults() > 0) {
					try (Pipeline pipeline = jedis.pipelined()) {
						for (redis.clients.jedis.search.Document doc : searchResult.getDocuments()) {
							pipeline.jsonDel(doc.getId());
						}
						pipeline.syncAndReturnAll();
					}
				}
				else {
					moreRecords = false;
				}
			}
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating DefaultSemanticCache instances.
	 */
	public static class Builder {

		private VectorStore vectorStore;

		private EmbeddingModel embeddingModel;

		private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		private boolean useDistanceThreshold = false;

		private String indexName = DEFAULT_INDEX_NAME;

		private String prefix = DEFAULT_PREFIX;

		private JedisPooled jedisClient;

		// Builder methods with validation
		public Builder vectorStore(VectorStore vectorStore) {
			this.vectorStore = vectorStore;
			return this;
		}

		public Builder embeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return this;
		}

		public Builder similarityThreshold(double threshold) {
			this.similarityThreshold = threshold;
			return this;
		}

		public Builder distanceThreshold(double threshold) {
			this.similarityThreshold = threshold;
			this.useDistanceThreshold = true;
			return this;
		}

		public Builder indexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder prefix(String prefix) {
			this.prefix = prefix;
			return this;
		}

		public Builder jedisClient(JedisPooled jedisClient) {
			this.jedisClient = jedisClient;
			return this;
		}

		public DefaultSemanticCache build() {
			if (this.vectorStore == null) {
				if (this.jedisClient == null) {
					throw new IllegalStateException("Either vectorStore or jedisClient must be provided");
				}
				if (this.embeddingModel == null) {
					throw new IllegalStateException("EmbeddingModel must be provided");
				}
				this.vectorStore = RedisVectorStore.builder(this.jedisClient, this.embeddingModel)
					.indexName(this.indexName)
					.prefix(this.prefix)
					.metadataFields(MetadataField.text("response"), MetadataField.text("response_text"),
							MetadataField.numeric("ttl"))
					.initializeSchema(true)
					.build();
				if (this.vectorStore instanceof RedisVectorStore redisStore) {
					redisStore.afterPropertiesSet();
				}
			}
			return new DefaultSemanticCache(this.vectorStore, this.embeddingModel, this.similarityThreshold,
					this.indexName, this.prefix, this.useDistanceThreshold);
		}

	}

	/**
	 * Type adapter for serializing/deserializing Duration objects.
	 */
	private static class DurationAdapter implements JsonSerializer<Duration>, JsonDeserializer<Duration> {

		@Override
		public JsonElement serialize(Duration duration, Type type, JsonSerializationContext context) {
			return new JsonPrimitive(duration.toSeconds());
		}

		@Override
		public Duration deserialize(JsonElement json, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			return Duration.ofSeconds(json.getAsLong());
		}

	}

	/**
	 * Type adapter for serializing/deserializing ChatResponse objects.
	 */
	private static class ChatResponseAdapter implements JsonSerializer<ChatResponse>, JsonDeserializer<ChatResponse> {

		@Override
		public JsonElement serialize(ChatResponse response, Type type, JsonSerializationContext context) {
			JsonObject jsonObject = new JsonObject();

			// Store the exact text of the response
			String responseText = "";
			if (response.getResults() != null && !response.getResults().isEmpty()) {
				Message output = (Message) response.getResults().get(0).getOutput();
				if (output != null) {
					responseText = output.getText();
				}
			}
			jsonObject.addProperty("fullText", responseText);

			// Handle generations
			JsonArray generations = new JsonArray();
			for (Generation generation : response.getResults()) {
				JsonObject generationObj = new JsonObject();
				Message output = (Message) generation.getOutput();
				generationObj.addProperty("text", output.getText());
				generations.add(generationObj);
			}
			jsonObject.add("generations", generations);

			return jsonObject;
		}

		@Override
		public ChatResponse deserialize(JsonElement json, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject jsonObject = json.getAsJsonObject();

			// Get the exact stored text for the response
			String fullText = "";
			if (jsonObject.has("fullText")) {
				fullText = jsonObject.get("fullText").getAsString();
			}

			// If we have the full text, use it directly
			if (!fullText.isEmpty()) {
				List<Generation> generations = new ArrayList<>();
				generations.add(new Generation(new AssistantMessage(fullText)));
				return ChatResponse.builder().generations(generations).build();
			}

			// Fallback to the old approach if fullText is not available
			List<Generation> generations = new ArrayList<>();
			JsonArray generationsArray = jsonObject.getAsJsonArray("generations");
			for (JsonElement element : generationsArray) {
				JsonObject generationObj = element.getAsJsonObject();
				String text = generationObj.get("text").getAsString();
				generations.add(new Generation(new AssistantMessage(text)));
			}

			return ChatResponse.builder().generations(generations).build();
		}

	}

}
