/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.valkey;

import static glide.api.models.GlideString.gs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import glide.api.BaseClient;
import glide.api.commands.servermodules.FT;
import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DataType;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldHnsw;
import glide.api.models.commands.FT.FTSearchOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * Valkey-based vector store implementation using Valkey's vector search capabilities.
 *
 * <p>
 * The store uses Valkey JSON documents to persist vector embeddings along with their
 * associated document content and metadata. It leverages Valkey's FT.CREATE and FT.SEARCH
 * commands for creating and querying vector similarity indexes.
 *
 * @author Vasile Gorcinschi
 * @since 1.0.0
 */
public class ValkeyVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final String DEFAULT_INDEX_NAME = "spring-ai-index";

	public static final String DEFAULT_PREFIX = "embedding:";

	public static final String DEFAULT_CONTENT_FIELD_NAME = "content";

	public static final String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";

	public static final Algorithm DEFAULT_VECTOR_ALGORITHM = Algorithm.HNSW;

	public static final DistanceMetric DEFAULT_DISTANCE_METRIC = DistanceMetric.COSINE;

	public static final String DISTANCE_FIELD_NAME = "vector_score";

	private static final String JSON_PATH_ROOT = "$";

	private static final String JSON_PATH_PREFIX = String.format("%s.", JSON_PATH_ROOT);

	private static final float EPSILON = 1e-10f;

	private static final Logger logger = LoggerFactory.getLogger(ValkeyVectorStore.class);

	private final BaseClient client;

	private final String indexName;

	private final String prefix;

	private final String contentFieldName;

	private final String embeddingFieldName;

	private final Algorithm vectorAlgorithm;

	private final List<MetadataField> metadataFields;

	private final boolean initializeSchema;

	private final FilterExpressionConverter filterExpressionConverter;

	private final ObjectMapper objectMapper;

	private final DistanceMetric distanceMetric;

	protected ValkeyVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.client, "Valkey client must not be null");

		this.client = builder.client;
		this.indexName = builder.indexName;
		this.prefix = builder.prefix;
		this.contentFieldName = builder.contentFieldName;
		this.embeddingFieldName = builder.embeddingFieldName;
		this.vectorAlgorithm = builder.vectorAlgorithm;
		this.distanceMetric = builder.distanceMetric;
		this.metadataFields = builder.metadataFields;
		this.initializeSchema = builder.initializeSchema;
		this.filterExpressionConverter = new ValkeyFilterExpressionConverter(this.metadataFields);
		this.objectMapper = JsonMapper.builder().build();
	}

	public BaseClient getBaseClient() {
		return this.client;
	}

	public boolean indexExists(String checkedIndexName) {
		return FT.list(client)
			.thenApply(indices -> Arrays.stream(indices).map(GlideString::toString).anyMatch(checkedIndexName::equals))
			.join();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.initializeSchema) {
			return;
		}

		if (indexExists(this.indexName)) {
			return;
		}

		// Build field definitions
		List<FieldInfo> fields = new ArrayList<>();

		// Content field
		fields.add(new FieldInfo(jsonPath(this.contentFieldName), this.contentFieldName, new TagField()));

		// Vector field
		if (this.vectorAlgorithm == Algorithm.HNSW) {
			fields.add(new FieldInfo(jsonPath(this.embeddingFieldName), this.embeddingFieldName,
					VectorFieldHnsw.builder(this.distanceMetric, this.embeddingModel.dimensions()).build()));
		}
		else {
			fields.add(new FieldInfo(jsonPath(this.embeddingFieldName), this.embeddingFieldName,
					VectorFieldFlat.builder(this.distanceMetric, this.embeddingModel.dimensions()).build()));
		}

		// Metadata fields
		for (MetadataField field : this.metadataFields) {
			fields.add(createFieldInfo(field));
		}

		// Create the index
		FT.create(this.client, this.indexName, fields.toArray(new FieldInfo[0]),
				FTCreateOptions.builder().dataType(DataType.JSON).prefixes(new String[] { this.prefix }).build())
			.join();

		logger.info("Created Valkey vector index: {}", this.indexName);
	}

	private FieldInfo createFieldInfo(MetadataField field) {
		String path = jsonPath(field.name());
		return switch (field.fieldType()) {
			case TAG -> new FieldInfo(path, field.name(), new TagField());
			case NUMERIC -> new FieldInfo(path, field.name(), new NumericField());
		};
	}

	private String jsonPath(String fieldName) {
		return JSON_PATH_PREFIX + fieldName;
	}

	@Override
	public void doAdd(List<Document> documents) {
		try {
			List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
					this.batchingStrategy);

			IntStream.range(0, documents.size()).mapToObj(i -> {
				Document document = documents.get(i);
				float[] embedding = embeddings.get(i);
				if (this.distanceMetric == DistanceMetric.COSINE) {
					embedding = normalize(embedding);
				}

				Map<String, Object> fields = new HashMap<>();
				fields.put(this.contentFieldName, document.getText());
				fields.put(this.embeddingFieldName, embedding);
				fields.putAll(document.getMetadata());

				String key = this.prefix + document.getId();
				return Json.set(this.client, key, "$", toJson(fields));
			}).forEach(CompletableFuture::join);

		}
		catch (Exception e) {
			throw new RuntimeException("Failed to add documents to Valkey", e);
		}
	}

	private float[] normalize(float[] vector) {
		// Calculate the magnitude of the vector
		float magnitude = 0.0f;
		for (float value : vector) {
			magnitude += value * value;
		}
		magnitude = (float) Math.sqrt(magnitude);

		// Avoid division by zero
		if (magnitude < EPSILON) {
			return vector;
		}

		// Normalize the vector
		float[] normalized = new float[vector.length];
		for (int i = 0; i < vector.length; i++) {
			normalized[i] = vector[i] / magnitude;
		}
		return normalized;
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String filterStr = this.filterExpressionConverter.convertExpression(filterExpression);

			// Valkey Search requires KNN syntax even for filter-only queries
			String query = String.format("(%s)=>[KNN %d @%s $BLOB]", filterStr, 10000, this.embeddingFieldName);

			float[] dummyEmbedding = new float[this.embeddingModel.dimensions()];
			FTSearchOptions options = FTSearchOptions.builder()
				.params(Map.of(gs("BLOB"), gs(floatArrayToBytes(dummyEmbedding))))
				.build();

			Object[] searchResult = FT.search(this.client, this.indexName, query, options).get();

			if (searchResult == null || searchResult.length < 2) {
				return;
			}

			@SuppressWarnings("unchecked")
			Map<GlideString, Map<GlideString, Object>> matchedResults = (Map<GlideString, Map<GlideString, Object>>) searchResult[1];

			matchedResults.keySet()
				.stream()
				.map(key -> client.del(new GlideString[] { key }))
				.forEach(CompletableFuture::join);

			logger.debug("Deleted {} documents matching filter", matchedResults.size());
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		try {
			for (String id : idList) {
				String key = this.prefix + id;
				this.client.del(new GlideString[] { gs(key) }).get();
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to delete documents from Valkey", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		try {
			Assert.isTrue(request.getTopK() > 0, "TopK must be greater than 0");

			float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());
			if (this.distanceMetric == DistanceMetric.COSINE) {
				queryEmbedding = normalize(queryEmbedding);
			}

			String filter = "*";
			if (request.getFilterExpression() != null) {
				filter = "(" + this.filterExpressionConverter.convertExpression(request.getFilterExpression()) + ")";
			}

			String query = String.format("%s=>[KNN %d @%s $BLOB AS %s]", filter, request.getTopK(),
					this.embeddingFieldName, DISTANCE_FIELD_NAME);

			FTSearchOptions options = FTSearchOptions.builder()
				.params(Map.of(gs("BLOB"), gs(floatArrayToBytes(queryEmbedding))))
				.build();

			Object[] result = FT.search(this.client, this.indexName, query, options).get();

			return parseSearchResults(result, request.getSimilarityThreshold());
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to search documents in Valkey", e);
		}
	}

	private record SimilarityData(double score, double distance) {
	}

	private SimilarityData extractSimilarityData(Map<GlideString, Object> fields) {

		double score = 0.0;
		GlideString scoreKey = gs(DISTANCE_FIELD_NAME);
		double distance = Double.parseDouble(fields.get(scoreKey).toString());
		if (fields.containsKey(scoreKey)) {
			double cosineScore = 1.0 - (distance / 2.0);
			score = switch (this.distanceMetric) {
				case COSINE -> cosineScore;
				case L2 -> 1.0 / (1.0 + distance);
				case IP -> {
					// Normalize IP scores to 0-1 range
					double normizalized = (distance + 1) / 2.0;
					yield Math.min(Math.max(normizalized, 0.0), 1.0);
				}
				default -> cosineScore;
			};
		}

		return new SimilarityData(score, distance);
	}

	private Optional<Document> parseDocument(Map.Entry<GlideString, Map<GlideString, Object>> entry,
			double similarityThreshold) {
		Map<GlideString, Object> fields = entry.getValue();

		SimilarityData similarityData = extractSimilarityData(fields);
		if (similarityData.score < similarityThreshold) {
			return Optional.empty();
		}

		GlideString jsonKey = gs(JSON_PATH_ROOT);
		if (!fields.containsKey(jsonKey)) {
			return Optional.empty();
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> docFields = this.objectMapper.readValue(fields.get(jsonKey).toString(), Map.class);
			String id = extractDocumentId(entry.getKey().toString());

			return Optional.of(buildDocument(id, docFields, similarityData));
		}
		catch (JsonProcessingException e) {
			logger.error("Failed to parse document JSON", e);

			return Optional.empty();
		}
	}

	private Document buildDocument(String id, Map<String, Object> docFields, SimilarityData similarityData) {
		String content = docFields.containsKey(this.contentFieldName) ? docFields.get(this.contentFieldName).toString()
				: "";
		Map<String, Object> metadata = extractMetadata(docFields, similarityData);

		return Document.builder().id(id).text(content).metadata(metadata).score(similarityData.score).build();
	}

	private Map<String, Object> extractMetadata(Map<String, Object> docFields, SimilarityData similarityData) {
		Map<String, Object> metadata = this.metadataFields.stream()
			.filter(metadataField -> docFields.containsKey(metadataField.name))
			.collect(Collectors.toMap(MetadataField::name, m -> docFields.get(m.name)));

		metadata.put(DISTANCE_FIELD_NAME, String.valueOf(similarityData.distance));
		metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - similarityData.score);
		return metadata;
	}

	private String extractDocumentId(String fullKey) {
		return fullKey.startsWith(this.prefix) ? fullKey.substring(this.prefix.length()) : fullKey;
	}

	@SuppressWarnings("unchecked")
	private List<Document> parseSearchResults(Object[] result, double similarityThreshold) {
		if (result == null || result.length < 2) {
			return List.of();
		}

		// result[0] is total count, result[1] is a map of id -> fields
		Map<GlideString, Map<GlideString, Object>> results = (Map<GlideString, Map<GlideString, Object>>) result[1];

		return results.entrySet()
			.stream()
			.map(entry -> parseDocument(entry, similarityThreshold))
			.flatMap(Optional::stream)
			.toList();
	}

	private static byte[] floatArrayToBytes(float[] floats) {
		ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (float f : floats) {
			buffer.putFloat(f);
		}
		return buffer.array();
	}

	private String toJson(Map<String, Object> map) {
		try {
			return this.objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize to JSON", e);
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		VectorStoreSimilarityMetric genericSimilarityMetric = switch (this.distanceMetric) {
			case COSINE -> VectorStoreSimilarityMetric.COSINE;
			case L2 -> VectorStoreSimilarityMetric.EUCLIDEAN;
			case IP -> VectorStoreSimilarityMetric.DOT;
			default -> throw new IllegalArgumentException("Unsupported distance metric:" + this.distanceMetric);
		};

		return VectorStoreObservationContext.builder("valkey", operationName)
			.collectionName(this.indexName)
			.dimensions(this.embeddingModel.dimensions())
			.fieldName(this.embeddingFieldName)
			.similarityMetric(genericSimilarityMetric.value());
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T nativeClient = (T) this.client;
		return Optional.of(nativeClient);
	}

	public static Builder builder(BaseClient client, EmbeddingModel embeddingModel) {
		return new Builder(client, embeddingModel);
	}

	public enum Algorithm {

		FLAT, HNSW

	}

	public enum FieldType {

		TAG, NUMERIC

	}

	public record MetadataField(String name, FieldType fieldType) {

		public static MetadataField tag(String name) {
			return new MetadataField(name, FieldType.TAG);
		}

		public static MetadataField numeric(String name) {
			return new MetadataField(name, FieldType.NUMERIC);
		}
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final BaseClient client;

		private String indexName = DEFAULT_INDEX_NAME;

		private String prefix = DEFAULT_PREFIX;

		private DistanceMetric distanceMetric = DEFAULT_DISTANCE_METRIC;

		private String contentFieldName = DEFAULT_CONTENT_FIELD_NAME;

		private String embeddingFieldName = DEFAULT_EMBEDDING_FIELD_NAME;

		private Algorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;

		private List<MetadataField> metadataFields = new ArrayList<>();

		private boolean initializeSchema = false;

		private Builder(BaseClient client, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(client, "Valkey client must not be null");
			this.client = client;
		}

		public Builder distanceMetric(DistanceMetric distanceMetric) {
			this.distanceMetric = distanceMetric;
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

		public Builder contentFieldName(String contentFieldName) {
			this.contentFieldName = contentFieldName;
			return this;
		}

		public Builder embeddingFieldName(String embeddingFieldName) {
			this.embeddingFieldName = embeddingFieldName;
			return this;
		}

		public Builder vectorAlgorithm(Algorithm vectorAlgorithm) {
			this.vectorAlgorithm = vectorAlgorithm;
			return this;
		}

		public Builder metadataFields(List<MetadataField> metadataFields) {
			this.metadataFields = metadataFields;
			return this;
		}

		public Builder metadataFields(MetadataField... metadataFields) {
			this.metadataFields = List.of(metadataFields);
			return this;
		}

		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		@Override
		public ValkeyVectorStore build() {
			return new ValkeyVectorStore(this);
		}

	}

}
