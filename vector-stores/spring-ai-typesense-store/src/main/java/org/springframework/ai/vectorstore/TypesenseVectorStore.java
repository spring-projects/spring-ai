/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
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

import io.micrometer.observation.ObservationRegistry;

/**
 * @author Pablo Sanchidrian Herrera
 * @author Soby Chacko
 * @author Christian Tzolov
 */
public class TypesenseVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(TypesenseVectorStore.class);

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

	private final Client client;

	private final EmbeddingModel embeddingModel;

	private final TypesenseVectorStoreConfig config;

	public final FilterExpressionConverter filterExpressionConverter = new TypesenseFilterExpressionConverter();

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	public static class TypesenseVectorStoreConfig {

		private final String collectionName;

		private final int embeddingDimension;

		public TypesenseVectorStoreConfig(String collectionName, int embeddingDimension) {
			this.collectionName = collectionName;
			this.embeddingDimension = embeddingDimension;
		}

		/**
		 * {@return the default config}
		 */
		public static TypesenseVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		private TypesenseVectorStoreConfig(Builder builder) {
			this.collectionName = builder.collectionName;
			this.embeddingDimension = builder.embeddingDimension;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {

			return new Builder();
		}

		public static class Builder {

			private String collectionName;

			private int embeddingDimension;

			/**
			 * Set the collection name.
			 * @param collectionName The collection name.
			 * @return The builder.
			 */
			public Builder withCollectionName(String collectionName) {
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Set the embedding dimension.
			 * @param embeddingDimension The embedding dimension.
			 * @return The builder.
			 */
			public Builder withEmbeddingDimension(int embeddingDimension) {
				this.embeddingDimension = embeddingDimension;
				return this;
			}

			/**
			 * Build the configuration.
			 * @return The configuration.
			 */
			public TypesenseVectorStoreConfig build() {
				return new TypesenseVectorStoreConfig(this);
			}

		}

	}

	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel) {
		this(client, embeddingModel, TypesenseVectorStoreConfig.defaultConfig(), false);
	}

	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel, TypesenseVectorStoreConfig config,
			boolean initializeSchema) {

		this(client, embeddingModel, config, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	public TypesenseVectorStore(Client client, EmbeddingModel embeddingModel, TypesenseVectorStoreConfig config,
			boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {

		super(observationRegistry, customObservationConvention);

		Assert.notNull(client, "Typesense must not be null");
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");

		this.client = client;
		this.embeddingModel = embeddingModel;
		this.config = config;
		this.initializeSchema = initializeSchema;
		this.batchingStrategy = batchingStrategy;
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");

		this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);

		List<HashMap<String, Object>> documentList = documents.stream().map(document -> {
			HashMap<String, Object> typesenseDoc = new HashMap<>();
			typesenseDoc.put(DOC_ID_FIELD_NAME, document.getId());
			typesenseDoc.put(CONTENT_FIELD_NAME, document.getContent());
			typesenseDoc.put(METADATA_FIELD_NAME, document.getMetadata());
			typesenseDoc.put(EMBEDDING_FIELD_NAME, document.getEmbedding());

			return typesenseDoc;
		}).toList();

		ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
		importDocumentsParameters.action("upsert");

		try {
			this.client.collections(this.config.collectionName)
				.documents()
				.import_(documentList, importDocumentsParameters);

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
			int deletedDocs = (Integer) this.client.collections(this.config.collectionName)
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
		multiSearchCollectionParameters.collection(this.config.collectionName);
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
					metadata.put("distance", hit.getVectorDistance());
					return new Document(docId, content, metadata);
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
		if (this.config.embeddingDimension != INVALID_EMBEDDING_DIMENSION) {
			return this.config.embeddingDimension;
		}
		try {
			int embeddingDimensions = this.embeddingModel.dimensions();
			if (embeddingDimensions > 0) {
				return embeddingDimensions;
			}
		}
		catch (Exception e) {
			logger.warn("Failed to obtain the embedding dimensions from the embedding model and fall backs to default:"
					+ this.config.embeddingDimension, e);
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
			this.client.collections(this.config.collectionName).retrieve();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	void createCollection() {
		if (this.hasCollection()) {
			logger.info("Collection {} already exists", this.config.collectionName);
			return;
		}

		CollectionSchema collectionSchema = new CollectionSchema();

		collectionSchema.name(this.config.collectionName)
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
			logger.info("Collection {} created", this.config.collectionName);
		}
		catch (Exception e) {
			logger.error("Failed to create collection {}", this.config.collectionName, e);
		}
	}

	void dropCollection() {
		if (!this.hasCollection()) {
			logger.info("Collection {} does not exist", this.config.collectionName);
			return;
		}

		try {
			this.client.collections(this.config.collectionName).delete();
			logger.info("Collection {} dropped", this.config.collectionName);
		}
		catch (Exception e) {
			logger.error("Failed to drop collection {}", this.config.collectionName, e);
		}
	}

	Map<String, Object> getCollectionInfo() {
		try {
			CollectionResponse retrievedCollection = this.client.collections(this.config.collectionName).retrieve();
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
			.withDimensions(this.embeddingModel.dimensions())
			.withCollectionName(this.config.collectionName)
			.withFieldName(EMBEDDING_FIELD_NAME)
			.withSimilarityMetric(VectorStoreSimilarityMetric.COSINE.value());
	}

}
