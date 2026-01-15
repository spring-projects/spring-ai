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

package org.springframework.ai.vectorstore.mongodb.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mongodb.MongoCommandException;
import com.mongodb.client.result.DeleteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;

/**
 * MongoDB Atlas-based vector store implementation using the Atlas Vector Search.
 *
 * <p>
 * The store uses a MongoDB collection to persist vector embeddings along with their
 * associated document content and metadata. By default, it uses the "vector_store"
 * collection with a vector search index for similarity search operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable collection and index
 * creation</li>
 * <li>Support for cosine similarity search</li>
 * <li>Metadata filtering using MongoDB Atlas Search syntax</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable batching strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * MongoDBAtlasVectorStore vectorStore = MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
 *     .collectionName("vector_store")
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
 *         .withFilterExpression("key1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * MongoDBAtlasVectorStore vectorStore = MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
 *     .collectionName("custom_vectors")
 *     .vectorIndexName("custom_vector_index")
 *     .pathName("custom_embedding")
 *     .numCandidates(500)
 *     .metadataFieldsToFilter(List.of("category", "author"))
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .build();
 * }</pre>
 *
 * <p>
 * Database Requirements:
 * </p>
 * <ul>
 * <li>MongoDB Atlas cluster with Vector Search enabled</li>
 * <li>Collection with vector search index configured</li>
 * <li>Collection schema with id (string), content (string), metadata (document), and
 * embedding (vector) fields</li>
 * <li>Proper access permissions for index and collection operations</li>
 * </ul>
 *
 * @author Chris Smith
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author chabinhwang
 * @since 1.0.0
 */
public class MongoDBAtlasVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(MongoDBAtlasVectorStore.class);

	public static final String ID_FIELD_NAME = "_id";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String SCORE_FIELD_NAME = "score";

	public static final String DEFAULT_VECTOR_COLLECTION_NAME = "vector_store";

	private static final String DEFAULT_VECTOR_INDEX_NAME = "vector_index";

	private static final String DEFAULT_PATH_NAME = "embedding";

	private static final int DEFAULT_NUM_CANDIDATES = 200;

	private static final int INDEX_ALREADY_EXISTS_ERROR_CODE = 68;

	private static final String INDEX_ALREADY_EXISTS_ERROR_CODE_NAME = "IndexAlreadyExists";

	private final MongoTemplate mongoTemplate;

	private final String collectionName;

	private final String vectorIndexName;

	private final String pathName;

	private final List<String> metadataFieldsToFilter;

	private final int numCandidates;

	private final MongoDBAtlasFilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	protected MongoDBAtlasVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.mongoTemplate, "MongoTemplate must not be null");

		this.mongoTemplate = builder.mongoTemplate;
		this.collectionName = builder.collectionName;
		this.vectorIndexName = builder.vectorIndexName;
		this.pathName = builder.pathName;
		this.numCandidates = builder.numCandidates;
		this.metadataFieldsToFilter = builder.metadataFieldsToFilter;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.initializeSchema = builder.initializeSchema;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.initializeSchema) {
			return;
		}

		// Create the collection if it does not exist
		if (!this.mongoTemplate.collectionExists(this.collectionName)) {
			this.mongoTemplate.createCollection(this.collectionName);
		}
		// Create search index
		createSearchIndex();
	}

	private void createSearchIndex() {
		try {
			this.mongoTemplate.executeCommand(createSearchIndexDefinition());
		}
		catch (UncategorizedMongoDbException e) {
			Throwable cause = e.getCause();
			if (cause instanceof MongoCommandException commandException) {
				// Ignore any IndexAlreadyExists errors
				if (INDEX_ALREADY_EXISTS_ERROR_CODE == commandException.getCode()
						|| INDEX_ALREADY_EXISTS_ERROR_CODE_NAME.equals(commandException.getErrorCodeName())) {
					return;
				}
			}
			throw e;
		}
	}

	/**
	 * Provides the Definition for the search index
	 */
	private org.bson.Document createSearchIndexDefinition() {
		List<org.bson.Document> vectorFields = new ArrayList<>();

		vectorFields.add(new org.bson.Document().append("type", "vector")
			.append("path", this.pathName)
			.append("numDimensions", this.embeddingModel.dimensions())
			.append("similarity", "cosine"));

		vectorFields.addAll(this.metadataFieldsToFilter.stream()
			.map(fieldName -> new org.bson.Document().append("type", "filter").append("path", "metadata." + fieldName))
			.toList());

		return new org.bson.Document().append("createSearchIndexes", this.collectionName)
			.append("indexes",
					List.of(new org.bson.Document().append("name", this.vectorIndexName)
						.append("type", "vectorSearch")
						.append("definition", new org.bson.Document("fields", vectorFields))));
	}

	/**
	 * Maps a Bson Document to a Spring AI Document
	 * @param mongoDocument the mongoDocument to map to a Spring AI Document
	 * @return the Spring AI Document
	 */
	private Document mapMongoDocument(org.bson.Document mongoDocument, float[] queryEmbedding) {
		String id = mongoDocument.getString(ID_FIELD_NAME);
		String content = mongoDocument.getString(CONTENT_FIELD_NAME);
		double score = mongoDocument.getDouble(SCORE_FIELD_NAME);
		Map<String, Object> metadata = mongoDocument.get(METADATA_FIELD_NAME, org.bson.Document.class);

		metadata.put(DocumentMetadata.DISTANCE.value(), 1 - score);

		// @formatter:off
		return Document.builder()
			.id(id)
			.text(content)
			.metadata(metadata)
			.score(score)
			.build(); // @formatter:on
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			MongoDBDocument mdbDocument = new MongoDBDocument(document.getId(),
					Objects.requireNonNullElse(document.getText(), ""),
					document.getMetadata(), embeddings.get(i));
			this.mongoTemplate.save(mdbDocument, this.collectionName);
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where(ID_FIELD_NAME).in(idList));
		this.mongoTemplate.remove(query, this.collectionName);
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String nativeFilterExpression = this.filterExpressionConverter.convertExpression(filterExpression);
			BasicQuery query = new BasicQuery(nativeFilterExpression);
			DeleteResult deleteResult = this.mongoTemplate.remove(query, this.collectionName);

			logger.debug("Deleted {} documents matching filter expression", deleteResult.getDeletedCount());
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(SearchRequest.builder().query(query).build());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		String nativeFilterExpressions = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());
		var vectorSearch = new VectorSearchAggregation(EmbeddingUtils.toList(queryEmbedding), this.pathName,
				this.numCandidates, this.vectorIndexName, request.getTopK(), nativeFilterExpressions);

		Aggregation aggregation = Aggregation.newAggregation(vectorSearch,
				Aggregation.addFields()
					.addField(SCORE_FIELD_NAME)
					.withValueOfExpression("{\"$meta\":\"vectorSearchScore\"}")
					.build(),
				Aggregation.match(new Criteria(SCORE_FIELD_NAME).gte(request.getSimilarityThreshold())));

		return this.mongoTemplate.aggregate(aggregation, this.collectionName, org.bson.Document.class)
			.getMappedResults()
			.stream()
			.map(d -> mapMongoDocument(d, queryEmbedding))
			.toList();
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.MONGODB.value(), operationName)
			.collectionName(this.collectionName)
			.dimensions(this.embeddingModel.dimensions())
			.fieldName(this.pathName);
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.mongoTemplate;
		return Optional.of(client);
	}

	/**
	 * Creates a new builder instance for MongoDBAtlasVectorStore.
	 * @return a new MongoDBBuilder instance
	 */
	public static Builder builder(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
		return new Builder(mongoTemplate, embeddingModel);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final MongoTemplate mongoTemplate;

		private String collectionName = DEFAULT_VECTOR_COLLECTION_NAME;

		private String vectorIndexName = DEFAULT_VECTOR_INDEX_NAME;

		private String pathName = DEFAULT_PATH_NAME;

		private int numCandidates = DEFAULT_NUM_CANDIDATES;

		private List<String> metadataFieldsToFilter = Collections.emptyList();

		private boolean initializeSchema = false;

		private MongoDBAtlasFilterExpressionConverter filterExpressionConverter = new MongoDBAtlasFilterExpressionConverter();

		/**
		 * @throws IllegalArgumentException if mongoTemplate is null
		 */
		private Builder(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(mongoTemplate, "MongoTemplate must not be null");
			this.mongoTemplate = mongoTemplate;
		}

		/**
		 * Configures the collection name. This must match the name of the collection for
		 * the Vector Search Index in Atlas.
		 * @param collectionName the name of the collection
		 * @return the builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "Collection Name must not be null or empty");
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the vector index name. This must match the name of the Vector Search
		 * Index Name in Atlas.
		 * @param vectorIndexName the name of the vector index
		 * @return the builder instance
		 * @throws IllegalArgumentException if vectorIndexName is null or empty
		 */
		public Builder vectorIndexName(String vectorIndexName) {
			Assert.hasText(vectorIndexName, "Vector Index Name must not be null or empty");
			this.vectorIndexName = vectorIndexName;
			return this;
		}

		/**
		 * Configures the path name. This must match the name of the field indexed for the
		 * Vector Search Index in Atlas.
		 * @param pathName the name of the path
		 * @return the builder instance
		 * @throws IllegalArgumentException if pathName is null or empty
		 */
		public Builder pathName(String pathName) {
			Assert.hasText(pathName, "Path Name must not be null or empty");
			this.pathName = pathName;
			return this;
		}

		/**
		 * Sets the number of candidates for vector search.
		 * @param numCandidates the number of candidates
		 * @return the builder instance
		 */
		public Builder numCandidates(int numCandidates) {
			this.numCandidates = numCandidates;
			return this;
		}

		/**
		 * Sets the metadata fields to filter in vector search.
		 * @param metadataFieldsToFilter list of metadata field names
		 * @return the builder instance
		 * @throws IllegalArgumentException if metadataFieldsToFilter is null or empty
		 */
		public Builder metadataFieldsToFilter(List<String> metadataFieldsToFilter) {
			Assert.notEmpty(metadataFieldsToFilter, "Fields list must not be empty");
			this.metadataFieldsToFilter = metadataFieldsToFilter;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the filter expression converter.
		 * @param converter the filter expression converter to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if converter is null
		 */
		public Builder filterExpressionConverter(MongoDBAtlasFilterExpressionConverter converter) {
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Builds the MongoDBAtlasVectorStore instance.
		 * @return a new MongoDBAtlasVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		@Override
		public MongoDBAtlasVectorStore build() {
			return new MongoDBAtlasVectorStore(this);
		}

	}

	/**
	 * The representation of {@link Document} along with its embedding.
	 *
	 * @param id The id of the document
	 * @param content The content of the document
	 * @param metadata The metadata of the document
	 * @param embedding The vectors representing the content of the document
	 */
	public record MongoDBDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

}
