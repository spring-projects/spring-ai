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

package org.springframework.ai.vectorstore.typesense;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentMetadata;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.model.CollectionResponse;
import org.typesense.model.CollectionSchema;
import org.typesense.model.DeleteDocumentsParameters;
import org.typesense.model.Field;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.model.MultiSearchCollectionParameters;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchSearchesParameter;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * A ObservationVectorStore implementation that uses Typesense as the underlying storage.
 *
 * <p>
 * The store uses Typesense's vector search functionality to persist and query vector
 * embeddings along with their associated document content and metadata. The
 * implementation leverages Typesense's efficient similarity search capabilities for k-NN
 * operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable collection creation</li>
 * <li>Support for cosine similarity search</li>
 * <li>Metadata filtering using Typesense's filter expressions</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * TypesenseVectorStore vectorStore = TypesenseVectorStore.builder()
 *     .client(client)
 *     .embeddingModel(embeddingModel)
 *     .initializeSchema(true)
 *     .build();
 *
 * // Add documents
 * vectorStore.add(List.of(
 *     new Document("content1", Map.of("key1", "value1")),
 *     new Document("content2", Map.of("key2", "value2"))
 * ));
 *
 * // Search with filters
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.query("search text")
 *         .withTopK(5)
 *         .withSimilarityThreshold(0.7)
 *         .withFilterExpression("metadata.key1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * TypesenseVectorStore vectorStore = TypesenseVectorStore.builder()
 *     .client(client)
 *     .embeddingModel(embeddingModel)
 *     .collectionName("custom_vectors")
 *     .embeddingDimension(1536)
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .observationRegistry(observationRegistry)
 *     .build();
 * }</pre>
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>Typesense server running and accessible</li>
 * <li>Collection schema with id (string), content (string), metadata (object), and
 * embedding (float array) fields</li>
 * </ul>
 *
 * @author Pablo Sanchidrian Herrera
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class TypesenseVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	/**
	 * The name of the field that contains the document ID. It is mandatory to set "id" as
	 * the field name because that is the name that typesense is going to look for.
	 */
	public static final String DOC_ID_FIELD_NAME = "id";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String EMBEDDING_FIELD_NAME = "embedding";

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final String DEFAULT_COLLECTION_NAME = "vector_store";

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	private static final Logger logger = LoggerFactory.getLogger(TypesenseVectorStore.class);

	public final FilterExpressionConverter filterExpressionConverter = new TypesenseFilterExpressionConverter();

	private final Client client;

	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	private final TypesenseVectorStoreConfig config;

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	private final String collectionName;

	private final int embeddingDimension;

	/**
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel) {
		this(client, embeddingModel, TypesenseVectorStoreConfig.defaultConfig(), false);
	}

	/**
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel, TypesenseVectorStoreConfig config,
			boolean initializeSchema) {
		this(client, embeddingModel, config, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	/**
	 * @deprecated Use {@link #builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel, TypesenseVectorStoreConfig config,
			boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {

		this(builder().client(client)
			.embeddingModel(embeddingModel)
			.collectionName(config.collectionName)
			.embeddingDimension(config.embeddingDimension)
			.initializeSchema(initializeSchema)
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention)
			.batchingStrategy(batchingStrategy));
	}

	/**
	 * Protected constructor for creating a TypesenseVectorStore instance using the
	 * builder pattern. This constructor initializes the vector store with the configured
	 * settings from the builder and performs necessary validations.
	 * @param builder the {@link TypesenseBuilder} containing all configuration settings
	 * @throws IllegalArgumentException if the client is null
	 * @throws IllegalArgumentException if the embeddingModel is null
	 * @see TypesenseBuilder
	 * @since 1.0.0
	 */
	protected TypesenseVectorStore(TypesenseBuilder builder) {
		super(builder);

		Assert.notNull(builder.client, "Typesense must not be null");

		this.client = builder.client;
		this.initializeSchema = builder.initializeSchema;
		this.batchingStrategy = builder.batchingStrategy;
		this.collectionName = builder.collectionName;
		this.embeddingDimension = builder.embeddingDimension;
		this.config = null;
	}

	/**
	 * Creates a new TypesenseBuilder instance. This is the recommended way to instantiate
	 * a TypesenseVectorStore.
	 * @return a new TypesenseBuilder instance
	 */
	public static TypesenseBuilder builder() {
		return new TypesenseBuilder();
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		List<HashMap<String, Object>> documentList = documents.stream().map(document -> {
			HashMap<String, Object> typesenseDoc = new HashMap<>();
			typesenseDoc.put(DOC_ID_FIELD_NAME, document.getId());
			typesenseDoc.put(CONTENT_FIELD_NAME, document.getContent());
			typesenseDoc.put(METADATA_FIELD_NAME, document.getMetadata());
			typesenseDoc.put(EMBEDDING_FIELD_NAME, embeddings.get(documents.indexOf(document)));

			return typesenseDoc;
		}).toList();

		ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
		importDocumentsParameters.action("upsert");

		try {
			this.client.collections(this.collectionName).documents().import_(documentList, importDocumentsParameters);

			logger.info("Added {} documents", documentList.size());
		}
		catch (Exception e) {
			logger.error("Failed to add documents", e);
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
		deleteDocumentsParameters.filterBy(DOC_ID_FIELD_NAME + ":=[" + String.join(",", idList) + "]");

		try {
			int deletedDocs = (Integer) this.client.collections(this.collectionName)
				.documents()
				.delete(deleteDocumentsParameters)
				.getOrDefault("num_deleted", 0);

			if (deletedDocs < idList.size()) {
				logger.warn("Failed to delete all documents");
			}

			return Optional.of(deletedDocs > 0);
		}
		catch (Exception e) {
			logger.error("Failed to delete documents", e);
			return Optional.of(Boolean.FALSE);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Assert.notNull(request.getQuery(), "Query string must not be null");

		String nativeFilterExpressions = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		logger.info("Filter expression: {}", nativeFilterExpressions);

		float[] embedding = this.embeddingModel.embed(request.getQuery());

		MultiSearchCollectionParameters multiSearchCollectionParameters = new MultiSearchCollectionParameters();
		multiSearchCollectionParameters.collection(this.collectionName);
		multiSearchCollectionParameters.q("*");

		Stream<Float> floatStream = IntStream.range(0, embedding.length).mapToObj(i -> embedding[i]);
		// typesense uses only cosine similarity
		String vectorQuery = EMBEDDING_FIELD_NAME + ":(" + "["
				+ String.join(",", floatStream.map(String::valueOf).toList()) + "], " + "k: " + request.getTopK() + ", "
				+ "distance_threshold: " + (1 - request.getSimilarityThreshold()) + ")";

		multiSearchCollectionParameters.vectorQuery(vectorQuery);
		multiSearchCollectionParameters.filterBy(nativeFilterExpressions);

		MultiSearchSearchesParameter multiSearchesParameter = new MultiSearchSearchesParameter()
			.addSearchesItem(multiSearchCollectionParameters);

		try {
			MultiSearchResult result = this.client.multiSearch.perform(multiSearchesParameter,
					Map.of("query_by", EMBEDDING_FIELD_NAME));

			List<Document> documents = result.getResults()
				.stream()
				.flatMap(searchResult -> searchResult.getHits().stream().map(hit -> {
					Map<String, Object> rawDocument = hit.getDocument();
					String docId = rawDocument.get(DOC_ID_FIELD_NAME).toString();
					String content = rawDocument.get(CONTENT_FIELD_NAME).toString();
					Map<String, Object> metadata = rawDocument.get(METADATA_FIELD_NAME) instanceof Map
							? (Map<String, Object>) rawDocument.get(METADATA_FIELD_NAME) : Map.of();
					metadata.put(DocumentMetadata.DISTANCE.value(), hit.getVectorDistance());
					return Document.builder()
						.id(docId)
						.text(content)
						.metadata(metadata)
						.score(1.0 - hit.getVectorDistance())
						.build();
				}))
				.toList();

			logger.info("Found {} documents", documents.size());
			return documents;
		}
		catch (Exception e) {
			logger.error("Failed to search documents", e);
			return List.of();
		}
	}

	int embeddingDimensions() {
		if (this.embeddingDimension != INVALID_EMBEDDING_DIMENSION) {
			return this.embeddingDimension;
		}
		try {
			int embeddingDimensions = this.embeddingModel.dimensions();
			if (embeddingDimensions > 0) {
				return embeddingDimensions;
			}
		}
		catch (Exception e) {
			logger.warn("Failed to obtain the embedding dimensions from the embedding model and fall backs to default:"
					+ this.embeddingDimension, e);
		}
		return OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

	// ---------------------------------------------------------------------------------
	// Initialization
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() {
		if (this.initializeSchema) {
			this.createCollection();
		}
	}

	private boolean hasCollection() {
		try {
			this.client.collections(this.collectionName).retrieve();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	void createCollection() {
		if (this.hasCollection()) {
			logger.info("Collection {} already exists", this.collectionName);
			return;
		}

		CollectionSchema collectionSchema = new CollectionSchema();

		collectionSchema.name(this.collectionName)
			.addFieldsItem(new Field().name(DOC_ID_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(CONTENT_FIELD_NAME).type(FieldTypes.STRING).optional(false))
			.addFieldsItem(new Field().name(METADATA_FIELD_NAME).type(FieldTypes.OBJECT).optional(true))
			.addFieldsItem(new Field().name(EMBEDDING_FIELD_NAME)
				.type(FieldTypes.FLOAT_ARRAY)
				.numDim(this.embeddingDimensions())
				.optional(false))
			.enableNestedFields(true);

		try {
			this.client.collections().create(collectionSchema);
			logger.info("Collection {} created", this.collectionName);
		}
		catch (Exception e) {
			logger.error("Failed to create collection {}", this.collectionName, e);
		}
	}

	void dropCollection() {
		if (!this.hasCollection()) {
			logger.info("Collection {} does not exist", this.collectionName);
			return;
		}

		try {
			this.client.collections(this.collectionName).delete();
			logger.info("Collection {} dropped", this.collectionName);
		}
		catch (Exception e) {
			logger.error("Failed to drop collection {}", this.collectionName, e);
		}
	}

	Map<String, Object> getCollectionInfo() {
		try {
			CollectionResponse retrievedCollection = this.client.collections(this.collectionName).retrieve();
			return Map.of("name", retrievedCollection.getName(), "num_documents",
					retrievedCollection.getNumDocuments());
		}
		catch (Exception e) {
			logger.error("Failed to retrieve collection info", e);
			return null;
		}

	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.TYPESENSE.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.collectionName)
			.fieldName(EMBEDDING_FIELD_NAME)
			.similarityMetric(VectorStoreSimilarityMetric.COSINE.value());
	}

	public static final class TypesenseBuilder extends AbstractVectorStoreBuilder<TypesenseBuilder> {

		private String collectionName = DEFAULT_COLLECTION_NAME;

		private int embeddingDimension = INVALID_EMBEDDING_DIMENSION;

		private Client client;

		private boolean initializeSchema = false;

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		/**
		 * Configures the Typesense client.
		 * @param client the client for Typesense operations
		 * @return this builder instance
		 * @throws IllegalArgumentException if client is null
		 */
		public TypesenseBuilder client(Client client) {
			Assert.notNull(client, "client must not be null");
			this.client = client;
			return this;
		}

		/**
		 * Configures the collection name.
		 * @param collectionName the collection name to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public TypesenseBuilder collectionName(String collectionName) {
			Assert.hasText(collectionName, "collectionName must not be empty");
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the dimension size of the embedding vectors.
		 * @param embeddingDimension The dimension of the embedding
		 * @return this builder instance
		 * @throws IllegalArgumentException if dimension is invalid
		 */
		public TypesenseBuilder embeddingDimension(int embeddingDimension) {
			Assert.isTrue(embeddingDimension > 0, "Embedding dimension must be greater than 0");
			this.embeddingDimension = embeddingDimension;
			return this;
		}

		/**
		 * Configures whether to initialize the collection schema automatically.
		 * @param initializeSchema true to initialize schema automatically
		 * @return this builder instance
		 */
		public TypesenseBuilder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Configures the strategy for batching operations.
		 * @param batchingStrategy the batching strategy to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if batchingStrategy is null
		 */
		public TypesenseBuilder batchingStrategy(BatchingStrategy batchingStrategy) {
			Assert.notNull(batchingStrategy, "batchingStrategy must not be null");
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		@Override
		public TypesenseVectorStore build() {
			validate();
			return new TypesenseVectorStore(this);
		}

	}

	/**
	 * @deprecated Use {@link TypesenseVectorStore#builder()} instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public static class TypesenseVectorStoreConfig {

		private final String collectionName;

		private final int embeddingDimension;

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public TypesenseVectorStoreConfig(String collectionName, int embeddingDimension) {
			this.collectionName = collectionName;
			this.embeddingDimension = embeddingDimension;
		}

		private TypesenseVectorStoreConfig(Builder builder) {
			this.collectionName = builder.collectionName;
			this.embeddingDimension = builder.embeddingDimension;
		}

		/**
		 * {@return the default config}
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public static TypesenseVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public static Builder builder() {

			return new Builder();
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public static class Builder {

			private String collectionName;

			private int embeddingDimension;

			/**
			 * Set the collection name.
			 * @param collectionName The collection name.
			 * @return The builder.
			 */
			@Deprecated(forRemoval = true, since = "1.0.0-M5")
			public Builder withCollectionName(String collectionName) {
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Set the embedding dimension.
			 * @param embeddingDimension The embedding dimension.
			 * @return The builder.
			 */
			@Deprecated(forRemoval = true, since = "1.0.0-M5")
			public Builder withEmbeddingDimension(int embeddingDimension) {
				this.embeddingDimension = embeddingDimension;
				return this;
			}

			/**
			 * Build the configuration.
			 * @return The configuration.
			 */
			@Deprecated(forRemoval = true, since = "1.0.0-M5")
			public TypesenseVectorStoreConfig build() {
				return new TypesenseVectorStoreConfig(this);
			}

		}

	}

}
