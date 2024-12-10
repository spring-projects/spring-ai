/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.redis.vectorstore;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentMetadata;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.RediSearchUtil;
import redis.clients.jedis.search.Schema.FieldType;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * The RedisVectorStore is for managing and querying vector data in a Redis database. It
 * offers functionalities like adding, deleting, and performing similarity searches on
 * documents.
 *
 * The store utilizes RedisJSON and RedisSearch to handle JSON documents and to index and
 * search vector data. It supports various vector algorithms (e.g., FLAT, HSNW) for
 * efficient similarity searches. Additionally, it allows for custom metadata fields in
 * the documents to be stored alongside the vector and content data.
 *
 * This class requires a RedisVectorStoreConfig configuration object for initialization,
 * which includes settings like Redis URI, index name, field names, and vector algorithms.
 * It also requires an EmbeddingModel to convert documents into embeddings before storing
 * them.
 *
 * @author Julien Ruaux
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Jihoon Kim
 * @see VectorStore
 * @see RedisVectorStoreConfig
 * @see EmbeddingModel
 */
public class RedisVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final String DEFAULT_INDEX_NAME = "spring-ai-index";

	public static final String DEFAULT_CONTENT_FIELD_NAME = "content";

	public static final String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";

	public static final String DEFAULT_PREFIX = "embedding:";

	public static final Algorithm DEFAULT_VECTOR_ALGORITHM = Algorithm.HSNW;

	public static final String DISTANCE_FIELD_NAME = "vector_score";

	private static final String QUERY_FORMAT = "%s=>[KNN %s @%s $%s AS %s]";

	private static final Path2 JSON_SET_PATH = Path2.of("$");

	private static final String JSON_PATH_PREFIX = "$.";

	private static final Logger logger = LoggerFactory.getLogger(RedisVectorStore.class);

	private static final Predicate<Object> RESPONSE_OK = Predicate.isEqual("OK");

	private static final Predicate<Object> RESPONSE_DEL_OK = Predicate.isEqual(1L);

	private static final String VECTOR_TYPE_FLOAT32 = "FLOAT32";

	private static final String EMBEDDING_PARAM_NAME = "BLOB";

	private static final String DEFAULT_DISTANCE_METRIC = "COSINE";

	private final JedisPooled jedis;

	private final boolean initializeSchema;

	private final String indexName;

	private final String prefix;

	private final String contentFieldName;

	private final String embeddingFieldName;

	private final Algorithm vectorAlgorithm;

	private final List<MetadataField> metadataFields;

	private final BatchingStrategy batchingStrategy;

	private final FilterExpressionConverter filterExpressionConverter;

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public RedisVectorStore(RedisVectorStoreConfig config, EmbeddingModel embeddingModel, JedisPooled jedis,
			boolean initializeSchema) {
		this(config, embeddingModel, jedis, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public RedisVectorStore(RedisVectorStoreConfig config, EmbeddingModel embeddingModel, JedisPooled jedis,
			boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {

		this(builder(jedis).embeddingModel(embeddingModel)
			.indexName(config.indexName)
			.prefix(config.prefix)
			.contentFieldName(config.contentFieldName)
			.embeddingFieldName(config.embeddingFieldName)
			.vectorAlgorithm(config.vectorAlgorithm)
			.metadataFields(config.metadataFields)
			.initializeSchema(initializeSchema)
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention)
			.batchingStrategy(batchingStrategy));
	}

	private RedisVectorStore(RedisBuilder builder) {
		super(builder);
		this.jedis = builder.jedis;
		this.indexName = builder.indexName;
		this.prefix = builder.prefix;
		this.contentFieldName = builder.contentFieldName;
		this.embeddingFieldName = builder.embeddingFieldName;
		this.vectorAlgorithm = builder.vectorAlgorithm;
		this.metadataFields = builder.metadataFields;
		this.initializeSchema = builder.initializeSchema;
		this.batchingStrategy = builder.batchingStrategy;
		this.filterExpressionConverter = new RedisFilterExpressionConverter(this.metadataFields);
	}

	public JedisPooled getJedis() {
		return this.jedis;
	}

	@Override
	public void doAdd(List<Document> documents) {
		try (Pipeline pipeline = this.jedis.pipelined()) {

			List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
					this.batchingStrategy);

			for (Document document : documents) {
				var fields = new HashMap<String, Object>();
				fields.put(this.embeddingFieldName, embeddings.get(documents.indexOf(document)));
				fields.put(this.contentFieldName, document.getContent());
				fields.putAll(document.getMetadata());
				pipeline.jsonSetWithEscape(key(document.getId()), JSON_SET_PATH, fields);
			}
			List<Object> responses = pipeline.syncAndReturnAll();
			Optional<Object> errResponse = responses.stream().filter(Predicate.not(RESPONSE_OK)).findAny();
			if (errResponse.isPresent()) {
				String message = MessageFormat.format("Could not add document: {0}", errResponse.get());
				if (logger.isErrorEnabled()) {
					logger.error(message);
				}
				throw new RuntimeException(message);
			}
		}
	}

	private String key(String id) {
		return this.prefix + id;
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		try (Pipeline pipeline = this.jedis.pipelined()) {
			for (String id : idList) {
				pipeline.jsonDel(key(id));
			}
			List<Object> responses = pipeline.syncAndReturnAll();
			Optional<Object> errResponse = responses.stream().filter(Predicate.not(RESPONSE_DEL_OK)).findAny();
			if (errResponse.isPresent()) {
				if (logger.isErrorEnabled()) {
					logger.error("Could not delete document: {}", errResponse.get());
				}
				return Optional.of(false);
			}
			return Optional.of(true);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		Assert.isTrue(request.getTopK() > 0, "The number of documents to be returned must be greater than zero");
		Assert.isTrue(request.getSimilarityThreshold() >= 0 && request.getSimilarityThreshold() <= 1,
				"The similarity score is bounded between 0 and 1; least to most similar respectively.");

		String filter = nativeExpressionFilter(request);

		String queryString = String.format(QUERY_FORMAT, filter, request.getTopK(), this.embeddingFieldName,
				EMBEDDING_PARAM_NAME, DISTANCE_FIELD_NAME);

		List<String> returnFields = new ArrayList<>();
		this.metadataFields.stream().map(MetadataField::name).forEach(returnFields::add);
		returnFields.add(this.embeddingFieldName);
		returnFields.add(this.contentFieldName);
		returnFields.add(DISTANCE_FIELD_NAME);
		var embedding = this.embeddingModel.embed(request.getQuery());
		Query query = new Query(queryString).addParam(EMBEDDING_PARAM_NAME, RediSearchUtil.toByteArray(embedding))
			.returnFields(returnFields.toArray(new String[0]))
			.setSortBy(DISTANCE_FIELD_NAME, true)
			.limit(0, request.getTopK())
			.dialect(2);

		SearchResult result = this.jedis.ftSearch(this.indexName, query);
		return result.getDocuments()
			.stream()
			.filter(d -> similarityScore(d) >= request.getSimilarityThreshold())
			.map(this::toDocument)
			.toList();
	}

	private Document toDocument(redis.clients.jedis.search.Document doc) {
		var id = doc.getId().substring(this.prefix.length());
		var content = doc.hasProperty(this.contentFieldName) ? doc.getString(this.contentFieldName) : "";
		Map<String, Object> metadata = this.metadataFields.stream()
			.map(MetadataField::name)
			.filter(doc::hasProperty)
			.collect(Collectors.toMap(Function.identity(), doc::getString));
		// TODO: this seems wrong. The key is named "vector_store", but the value is the
		// distance. Can we remove this after standardizing the metadata?
		metadata.put(DISTANCE_FIELD_NAME, 1 - similarityScore(doc));
		metadata.put(DocumentMetadata.DISTANCE.value(), 1 - similarityScore(doc));
		return Document.builder().id(id).text(content).metadata(metadata).score((double) similarityScore(doc)).build();
	}

	private float similarityScore(redis.clients.jedis.search.Document doc) {
		return (2 - Float.parseFloat(doc.getString(DISTANCE_FIELD_NAME))) / 2;
	}

	private String nativeExpressionFilter(SearchRequest request) {
		if (request.getFilterExpression() == null) {
			return "*";
		}
		return "(" + this.filterExpressionConverter.convertExpression(request.getFilterExpression()) + ")";
	}

	@Override
	public void afterPropertiesSet() {

		if (!this.initializeSchema) {
			return;
		}

		// If index already exists don't do anything
		if (this.jedis.ftList().contains(this.indexName)) {
			return;
		}

		String response = this.jedis.ftCreate(this.indexName,
				FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(this.prefix), schemaFields());
		if (!RESPONSE_OK.test(response)) {
			String message = MessageFormat.format("Could not create index: {0}", response);
			throw new RuntimeException(message);
		}
	}

	private Iterable<SchemaField> schemaFields() {
		Map<String, Object> vectorAttrs = new HashMap<>();
		vectorAttrs.put("DIM", this.embeddingModel.dimensions());
		vectorAttrs.put("DISTANCE_METRIC", DEFAULT_DISTANCE_METRIC);
		vectorAttrs.put("TYPE", VECTOR_TYPE_FLOAT32);
		List<SchemaField> fields = new ArrayList<>();
		fields.add(TextField.of(jsonPath(this.contentFieldName)).as(this.contentFieldName).weight(1.0));
		fields.add(VectorField.builder()
			.fieldName(jsonPath(this.embeddingFieldName))
			.algorithm(vectorAlgorithm())
			.attributes(vectorAttrs)
			.as(this.embeddingFieldName)
			.build());

		if (!CollectionUtils.isEmpty(this.metadataFields)) {
			for (MetadataField field : this.metadataFields) {
				fields.add(schemaField(field));
			}
		}
		return fields;
	}

	private SchemaField schemaField(MetadataField field) {
		String fieldName = jsonPath(field.name);
		return switch (field.fieldType) {
			case NUMERIC -> NumericField.of(fieldName).as(field.name);
			case TAG -> TagField.of(fieldName).as(field.name);
			case TEXT -> TextField.of(fieldName).as(field.name);
			default -> throw new IllegalArgumentException(
					MessageFormat.format("Field {0} has unsupported type {1}", field.name, field.fieldType));
		};
	}

	private VectorAlgorithm vectorAlgorithm() {
		if (this.vectorAlgorithm == Algorithm.HSNW) {
			return VectorAlgorithm.HNSW;
		}
		return VectorAlgorithm.FLAT;
	}

	private String jsonPath(String field) {
		return JSON_PATH_PREFIX + field;
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.REDIS.value(), operationName)
			.withCollectionName(this.indexName)
			.withDimensions(this.embeddingModel.dimensions())
			.withFieldName(this.embeddingFieldName)
			.withSimilarityMetric(VectorStoreSimilarityMetric.COSINE.value());

	}

	public enum Algorithm {

		FLAT, HSNW

	}

	public record MetadataField(String name, FieldType fieldType) {

		public static MetadataField text(String name) {
			return new MetadataField(name, FieldType.TEXT);
		}

		public static MetadataField numeric(String name) {
			return new MetadataField(name, FieldType.NUMERIC);
		}

		public static MetadataField tag(String name) {
			return new MetadataField(name, FieldType.TAG);
		}

	}

	public static RedisBuilder builder(JedisPooled jedis) {
		return new RedisBuilder(jedis);
	}

	public static class RedisBuilder extends AbstractVectorStoreBuilder<RedisBuilder> {

		private final JedisPooled jedis;

		private String indexName = DEFAULT_INDEX_NAME;

		private String prefix = DEFAULT_PREFIX;

		private String contentFieldName = DEFAULT_CONTENT_FIELD_NAME;

		private String embeddingFieldName = DEFAULT_EMBEDDING_FIELD_NAME;

		private Algorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;

		private List<MetadataField> metadataFields = new ArrayList<>();

		private boolean initializeSchema = false;

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		public RedisBuilder(JedisPooled jedis) {
			Assert.notNull(jedis, "JedisPooled must not be null");
			this.jedis = jedis;
		}

		/**
		 * Sets the Redis index name.
		 * @param indexName the index name to use
		 * @return the builder instance
		 */
		public RedisBuilder indexName(String indexName) {
			if (StringUtils.hasText(indexName)) {
				this.indexName = indexName;
			}
			return this;
		}

		/**
		 * Sets the Redis key prefix (default: "embedding:").
		 * @param prefix the prefix to use
		 * @return the builder instance
		 */
		public RedisBuilder prefix(String prefix) {
			if (StringUtils.hasText(prefix)) {
				this.prefix = prefix;
			}
			return this;
		}

		/**
		 * Sets the Redis content field name.
		 * @param fieldName the content field name to use
		 * @return the builder instance
		 */
		public RedisBuilder contentFieldName(String fieldName) {
			if (StringUtils.hasText(fieldName)) {
				this.contentFieldName = fieldName;
			}
			return this;
		}

		/**
		 * Sets the Redis embedding field name.
		 * @param fieldName the embedding field name to use
		 * @return the builder instance
		 */
		public RedisBuilder embeddingFieldName(String fieldName) {
			if (StringUtils.hasText(fieldName)) {
				this.embeddingFieldName = fieldName;
			}
			return this;
		}

		/**
		 * Sets the Redis vector algorithm.
		 * @param algorithm the vector algorithm to use
		 * @return the builder instance
		 */
		public RedisBuilder vectorAlgorithm(Algorithm algorithm) {
			if (algorithm != null) {
				this.vectorAlgorithm = algorithm;
			}
			return this;
		}

		/**
		 * Sets the metadata fields.
		 * @param fields the metadata fields to include
		 * @return the builder instance
		 */
		public RedisBuilder metadataFields(MetadataField... fields) {
			return metadataFields(Arrays.asList(fields));
		}

		/**
		 * Sets the metadata fields.
		 * @param fields the list of metadata fields to include
		 * @return the builder instance
		 */
		public RedisBuilder metadataFields(List<MetadataField> fields) {
			if (fields != null && !fields.isEmpty()) {
				this.metadataFields = new ArrayList<>(fields);
			}
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public RedisBuilder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the batching strategy.
		 * @param batchingStrategy the strategy to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if batchingStrategy is null
		 */
		public RedisBuilder batchingStrategy(BatchingStrategy batchingStrategy) {
			Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		@Override
		public RedisVectorStore build() {
			validate();
			return new RedisVectorStore(this);
		}

	}

	/**
	 * Configuration for the Redis vector store.
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public static final class RedisVectorStoreConfig {

		private final String indexName;

		private final String prefix;

		private final String contentFieldName;

		private final String embeddingFieldName;

		private final Algorithm vectorAlgorithm;

		private final List<MetadataField> metadataFields;

		private RedisVectorStoreConfig() {
			this(builder());
		}

		private RedisVectorStoreConfig(Builder builder) {
			this.indexName = builder.indexName;
			this.prefix = builder.prefix;
			this.contentFieldName = builder.contentFieldName;
			this.embeddingFieldName = builder.embeddingFieldName;
			this.vectorAlgorithm = builder.vectorAlgorithm;
			this.metadataFields = builder.metadataFields;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * {@return the default config}
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static RedisVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public static final class Builder {

			private String indexName = DEFAULT_INDEX_NAME;

			private String prefix = DEFAULT_PREFIX;

			private String contentFieldName = DEFAULT_CONTENT_FIELD_NAME;

			private String embeddingFieldName = DEFAULT_EMBEDDING_FIELD_NAME;

			private Algorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;

			private List<MetadataField> metadataFields = new ArrayList<>();

			private Builder() {
			}

			/**
			 * Configures the Redis index name to use.
			 * @param name the index name to use
			 * @return this builder
			 */
			public Builder withIndexName(String name) {
				this.indexName = name;
				return this;
			}

			/**
			 * Configures the Redis key prefix to use (default: "embedding:").
			 * @param prefix the prefix to use
			 * @return this builder
			 */
			public Builder withPrefix(String prefix) {
				this.prefix = prefix;
				return this;
			}

			/**
			 * Configures the Redis content field name to use.
			 * @param name the content field name to use
			 * @return this builder
			 */
			public Builder withContentFieldName(String name) {
				this.contentFieldName = name;
				return this;
			}

			/**
			 * Configures the Redis embedding field name to use.
			 * @param name the embedding field name to use
			 * @return this builder
			 */
			public Builder withEmbeddingFieldName(String name) {
				this.embeddingFieldName = name;
				return this;
			}

			/**
			 * Configures the Redis vector algorithm to use.
			 * @param algorithm the vector algorithm to use
			 * @return this builder
			 */
			public Builder withVectorAlgorithm(Algorithm algorithm) {
				this.vectorAlgorithm = algorithm;
				return this;
			}

			public Builder withMetadataFields(MetadataField... fields) {
				return withMetadataFields(Arrays.asList(fields));
			}

			public Builder withMetadataFields(List<MetadataField> fields) {
				this.metadataFields = fields;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 */
			public RedisVectorStoreConfig build() {

				return new RedisVectorStoreConfig(this);
			}

		}

	}

}
