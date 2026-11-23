/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.couchbase;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.couchbase.client.core.error.IndexExistsException;
import com.couchbase.client.core.error.IndexNotFoundException;
import com.couchbase.client.core.util.ConsistencyUtil;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import com.couchbase.client.java.manager.collection.CollectionSpec;
import com.couchbase.client.java.manager.collection.ScopeSpec;
import com.couchbase.client.java.manager.query.CreatePrimaryQueryIndexOptions;
import com.couchbase.client.java.manager.search.SearchIndex;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetrySpec;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * @author Laurent Doguin
 * @author chabinhwang
 * @since 1.0.0
 */
public class CouchbaseSearchVectorStore extends AbstractObservationVectorStore
		implements InitializingBean, AutoCloseable {

	private static final Log logger = LogFactory.getLog(CouchbaseSearchVectorStore.class);

	private static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	private static final String DEFAULT_COLLECTION_NAME = "_default";

	private static final String DEFAULT_SCOPE_NAME = "_default";

	private static final String DEFAULT_BUCKET_NAME = "default";

	private static final String EMBEDDING_FIELD = "embedding";

	private static final int UNKNOWN_DIMENSIONS = -1;

	private static final int INDEX_READ_BACK_ATTEMPTS = 5;

	private static final Duration INDEX_READ_BACK_DELAY = Duration.ofMillis(500);

	private final EmbeddingModel embeddingModel;

	private final String collectionName;

	private final String scopeName;

	private final String bucketName;

	private final String vectorIndexName;

	private final @Nullable Integer dimensions;

	// Cache for vectorDimensions(); a failed or missing read is not stored.
	private volatile int resolvedVectorDimensions = UNKNOWN_DIMENSIONS;

	private final CouchbaseSimilarityFunction similarityFunction;

	private final CouchbaseIndexOptimization indexOptimization;

	private final Cluster cluster;

	private final CouchbaseAiSearchFilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	private final Collection collection;

	private final Scope scope;

	private final Bucket bucket;

	protected CouchbaseSearchVectorStore(Builder builder) {
		super(builder);

		Objects.requireNonNull(builder.cluster, "CouchbaseCluster must not be null");
		this.initializeSchema = builder.initializeSchema;
		this.embeddingModel = Objects.requireNonNull(builder.getEmbeddingModel(), "embeddingModel must not be null");
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.cluster = builder.cluster;
		this.bucket = this.cluster.bucket(builder.bucketName);
		this.scope = this.bucket.scope(builder.scopeName);
		this.collection = this.scope.collection(builder.collectionName);
		this.vectorIndexName = builder.vectorIndexName;
		this.collectionName = builder.collectionName;
		this.bucketName = builder.bucketName;
		this.scopeName = builder.scopeName;
		this.dimensions = builder.dimensions;
		this.similarityFunction = builder.similarityFunction;
		this.indexOptimization = builder.indexOptimization;
	}

	@Override
	public void afterPropertiesSet() {

		if (!this.initializeSchema) {
			return;
		}

		try {
			logger.info("Init Cluster Called");
			initCluster();
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void doAdd(List<Document> documents) {
		logger.info("Trying Add");
		logger.info(this.bucketName);
		logger.info(this.scopeName);
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		for (int i = 0; i < documents.size(); i++) {
			writeDocument(documents.get(i), embeddings.get(i));
		}
	}

	/**
	 * Writes the whole document under its id, replacing any document already stored
	 * there, including metadata keys the new document no longer has.
	 */
	private void writeDocument(Document document, float[] embedding) {
		CouchbaseDocument cbDoc = new CouchbaseDocument(document.getId(),
				Objects.requireNonNullElse(document.getText(), ""), document.getMetadata(), embedding);
		this.collection.upsert(document.getId(), cbDoc);
	}

	@Override
	protected boolean supportsUpsert() {
		return true;
	}

	@Override
	protected void doUpsert(List<EmbeddedDocument> entries) {
		// Couchbase stores a vector of the wrong size without complaint and the search
		// index then skips the document, so it can never be found. So the whole batch is
		// checked first, and nothing is written when the size is unknown.
		int expected = vectorDimensions();
		if (expected == UNKNOWN_DIMENSIONS) {
			throw new IllegalStateException("Could not determine the vector size of search index "
					+ this.vectorIndexName + ". Make sure the index exists and can be read.");
		}
		for (int i = 0; i < entries.size(); i++) {
			checkDimensions("Embedding at index " + i, entries.get(i).embedding().length, expected);
		}
		for (EmbeddedDocument entry : entries) {
			writeDocument(entry.document(), entry.embedding());
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		for (String id : idList) {
			this.collection.remove(id);
		}
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");
		try {
			String nativeFilter = this.filterExpressionConverter.convertExpression(filterExpression);
			String sql = String.format("DELETE FROM %s WHERE %s", this.collection.name(), nativeFilter);
			this.scope.query(sql, QueryOptions.queryOptions().metrics(true));
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to delete documents by filter: " + e.getMessage(), e);
			}
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		// Convert the filter first, so an invalid one fails before the model is called
		String nativeFilterExpression = nativeFilterExpression(request);
		float[] embedding = this.embeddingModel.embed(request.getQuery());
		return searchByEmbedding(embedding, nativeFilterExpression, request);
	}

	@Override
	protected boolean supportsQueryEmbeddingSearch() {
		return true;
	}

	@Override
	protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
		// Couchbase rejects a query vector of the wrong size with a misleading "no search
		// index" error; checking here gives a clear one whenever the size is known.
		checkDimensions("Query embedding", queryEmbedding.length, vectorDimensions());
		return searchByEmbedding(queryEmbedding, nativeFilterExpression(request), request);
	}

	private String nativeFilterExpression(SearchRequest request) {
		Filter.Expression fe = request.getFilterExpression();
		return (fe != null) ? " AND " + this.filterExpressionConverter.convertExpression(fe) : "";
	}

	private List<Document> searchByEmbedding(float[] embedding, String nativeFilterExpression, SearchRequest request) {
		int topK = request.getTopK();
		double similarityThreshold = request.getSimilarityThreshold();
		// SEARCH() only filters the rows, so they are sorted by score explicitly
		String statement = String.format(
				"""
						SELECT c.* FROM `%s` AS c
						WHERE SEARCH_SCORE() > %s AND SEARCH(`c`, {"query": {"match_none": {}}, "knn": [{"field": "embedding", "k": %s, "vector": %s }   ]    }, {"index": "%s.%s.%s"}   )
						%s
						ORDER BY SEARCH_SCORE() DESC
						""",
				this.collectionName, similarityThreshold, topK, Arrays.toString(embedding), this.bucketName,
				this.scopeName, this.vectorIndexName, nativeFilterExpression);

		QueryResult result = this.scope.query(statement, QueryOptions.queryOptions());

		// Deserialize to CouchbaseDocument then map to Document: Couchbase SDK uses its
		// own bundled Jackson 2 (com.couchbase.client.core.deps) for rowsAs(); Spring AI
		// Document cannot be deserialized from our stored shape (id, content, metadata,
		// embedding)
		// by that ObjectMapper, so we use the store's native type and convert explicitly.
		return result.rowsAs(CouchbaseDocument.class)
			.stream()
			.map(cbDoc -> new Document(cbDoc.id(), cbDoc.content(), cbDoc.metadata()))
			.toList();
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this;
		return Optional.of(client);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		VectorStoreObservationContext.Builder builder = VectorStoreObservationContext
			.builder(VectorStoreProvider.COUCHBASE.value(), operationName)
			.collectionName(this.collection.name());
		int dimensions = vectorDimensions();
		if (dimensions > 0) {
			builder.dimensions(dimensions);
		}
		return builder;
	}

	public static Builder builder(Cluster cluster, EmbeddingModel embeddingModel) {
		return new Builder(cluster, embeddingModel);
	}

	public void initCluster() throws InterruptedException {
		// init scope, collection, indexes
		BucketSettings bucketSettings = this.cluster.buckets().getAllBuckets().get(this.bucketName);
		if (bucketSettings == null) {
			this.cluster.buckets().createBucket(BucketSettings.create(this.bucketName));
		}
		logger.info("Created bucket");
		Bucket targetBucket = this.cluster.bucket(this.bucketName);
		targetBucket.waitUntilReady(Duration.ofSeconds(20));
		logger.info("Opened Bucket");
		boolean scopeExist = targetBucket.collections()
			.getAllScopes()
			.stream()
			.anyMatch(scopeSpec -> scopeSpec.name().equals(this.scopeName));
		if (!scopeExist) {
			targetBucket.collections().createScope(this.scopeName);
		}
		ConsistencyUtil.waitUntilScopePresent(this.cluster.core(), this.bucketName, this.scopeName);
		Scope targetScope = targetBucket.scope(this.scopeName);
		boolean collectionExist = this.bucket.collections()
			.getAllScopes()
			.stream()
			.map(ScopeSpec::collections)
			.flatMap(java.util.Collection::stream)
			.filter(it -> it.scopeName().equals(this.scopeName))
			.map(CollectionSpec::name)
			.anyMatch(this.collectionName::equals);
		if (!collectionExist) {
			targetBucket.collections().createCollection(this.scopeName, this.collectionName);
			ConsistencyUtil.waitUntilCollectionPresent(this.cluster.core(), this.bucketName, this.scopeName,
					this.collectionName);
			Collection newCollection = targetScope.collection(this.collectionName);
			Mono.empty()
				.then(Mono.fromRunnable(
						() -> newCollection.async()
							.queryIndexes()
							.createPrimaryIndex(CreatePrimaryQueryIndexOptions.createPrimaryQueryIndexOptions()
								.ignoreIfExists(true))))
				.retryWhen(RetrySpec.backoff(3, Duration.ofMillis(1000)));
		}

		// Ask the model for the size only when the index is known to be missing. Any
		// other lookup failure fails startup, since the index may well exist.
		Integer existingDimensions;
		try {
			existingDimensions = readIndexDimensions();
		}
		catch (IllegalStateException ex) {
			// The index exists but has no usable vector field; the message says so
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Could not check whether search index " + this.vectorIndexName + " exists",
					ex);
		}
		if (existingDimensions == null) {
			int dimensions = (this.dimensions != null) ? this.dimensions : this.embeddingModel.dimensions();
			String jsonIndexTemplate = """
					  {
					  "type": "fulltext-index",
					  "name": "%s",
					  "sourceType": "gocbcore",
					  "sourceName": "%s",
					  "planParams": {
					    "maxPartitionsPerPIndex": 1024,
					    "indexPartitions": 1
					  },
					  "params": {
					    "doc_config": {
					      "docid_prefix_delim": "",
					      "docid_regexp": "",
					      "mode": "scope.collection.type_field",
					      "type_field": "type"
					    },
					    "mapping": {
					      "analysis": {},
					      "default_analyzer": "standard",
					      "default_datetime_parser": "dateTimeOptional",
					      "default_field": "_all",
					      "default_mapping": {
					        "dynamic": false,
					        "enabled": false
					      },
					      "default_type": "%s",
					      "docvalues_dynamic": false,
					      "index_dynamic": false,
					      "store_dynamic": false,
					      "type_field": "_type",
					      "types": {
					        "%s.%s": {
					          "dynamic": false,
					          "enabled": true,
					          "properties": {
					            "embedding": {
					              "dynamic": false,
					              "enabled": true,
					              "fields": [
					                {
					                  "dims": %s,
					                  "index": true,
					                  "name": "embedding",
					                  "similarity": "%s",
					                  "type": "vector",
					                  "vector_index_optimized_for": "%s"
					                }
					              ]
					            },
					            "content": {
					              "dynamic": false,
					              "enabled": true,
					              "fields": [
					                {
					                  "analyzer": "keyword",
					                  "docvalues": true,
					                  "include_in_all": true,
					                  "include_term_vectors": true,
					                  "index": true,
					                  "name": "text",
					                  "store": true,
					                  "type": "text"
					                }
					              ]
					            }
					          }
					        }
					      }
					    },
					    "store": {
					      "indexType": "scorch",
					      "segmentVersion": 16
					    }
					  },
					  "sourceParams": {}
					}
					""";
			String jsonIndexValue = String.format(jsonIndexTemplate, this.vectorIndexName, this.bucketName,
					this.collectionName, this.scopeName, this.collectionName, dimensions, this.similarityFunction,
					this.indexOptimization);

			SearchIndex searchIndex = SearchIndex.fromJson(jsonIndexValue);
			try {
				targetScope.searchIndexes().upsertIndex(searchIndex);
			}
			catch (IndexExistsException ex) {
				// Another process created it first, possibly with another size
				logger.debug("Search index " + this.vectorIndexName + " was created concurrently", ex);
			}

			// On a cluster with several search nodes, a new index can take a moment to
			// be visible everywhere, so the read is retried a few times.
			for (int attempt = 1; existingDimensions == null && attempt <= INDEX_READ_BACK_ATTEMPTS; attempt++) {
				if (attempt > 1) {
					Thread.sleep(INDEX_READ_BACK_DELAY.toMillis());
				}
				try {
					existingDimensions = readIndexDimensions();
				}
				catch (IllegalStateException ex) {
					throw ex;
				}
				catch (RuntimeException ex) {
					throw new IllegalStateException(
							"Could not read search index " + this.vectorIndexName + " after creating it", ex);
				}
			}
			if (existingDimensions == null) {
				throw new IllegalStateException(
						"Search index " + this.vectorIndexName + " does not exist after creating it");
			}
		}
		// A configured size is used as is, so it has to match the index: Couchbase
		// stores vectors of another size but never finds them in a search
		if (this.dimensions != null && !this.dimensions.equals(existingDimensions)) {
			throw new IllegalStateException(
					"The configured dimensions (" + this.dimensions + ") do not match the vector size of search index "
							+ this.vectorIndexName + " (" + existingDimensions + ")");
		}
		this.resolvedVectorDimensions = existingDimensions;
	}

	/**
	 * The size of the stored vectors.
	 * <p>
	 * The size comes from the configured {@code dimensions} if set, otherwise from the
	 * vector field of the search index, including an index this store just created. A
	 * failed read or a missing index leaves the size unknown, and the index is read again
	 * on the next call. Only a successful read is cached, so a store has to be rebuilt
	 * after the index is recreated with a different size.
	 * @return the known dimension, or {@code -1} when unknown
	 */
	private int vectorDimensions() {
		if (this.dimensions != null) {
			return this.dimensions;
		}
		int cached = this.resolvedVectorDimensions;
		if (cached != UNKNOWN_DIMENSIONS) {
			return cached;
		}
		try {
			Integer dimensions = readIndexDimensions();
			if (dimensions != null) {
				this.resolvedVectorDimensions = dimensions;
				return dimensions;
			}
		}
		catch (RuntimeException ex) {
			logger.debug("Could not read the vector dimensions from the search index", ex);
		}
		return UNKNOWN_DIMENSIONS;
	}

	/**
	 * Reads the {@code dims} of the {@code embedding} vector field from the search index
	 * definition.
	 * @return the size, or {@code null} when there is no such index
	 * @throws IllegalStateException when the index has no vector field for this scope and
	 * collection
	 */
	private @Nullable Integer readIndexDimensions() {
		SearchIndex index;
		try {
			index = this.scope.searchIndexes().getIndex(this.vectorIndexName);
		}
		catch (IndexNotFoundException ex) {
			return null;
		}
		Integer dimensions = vectorFieldDimensions(index.params(), this.scopeName + "." + this.collectionName);
		if (dimensions == null) {
			throw new IllegalStateException("Search index " + this.vectorIndexName + " has no vector field '"
					+ EMBEDDING_FIELD + "' with dims for " + this.scopeName + "." + this.collectionName);
		}
		return dimensions;
	}

	/**
	 * Finds {@code mapping.types[type].properties.embedding.fields[].dims} for the vector
	 * field in the given index parameters. The field must be indexed as
	 * {@code embedding}, the name the search query uses; a field without a name is
	 * indexed under its property name.
	 */
	private static @Nullable Integer vectorFieldDimensions(Map<String, Object> params, String type) {
		if (!(params.get("mapping") instanceof Map<?, ?> mapping) || !(mapping.get("types") instanceof Map<?, ?> types)
				|| !(types.get(type) instanceof Map<?, ?> typeMapping)
				|| !(typeMapping.get("properties") instanceof Map<?, ?> properties)
				|| !(properties.get(EMBEDDING_FIELD) instanceof Map<?, ?> embedding)
				|| !(embedding.get("fields") instanceof List<?> fields)) {
			return null;
		}
		for (Object field : fields) {
			if (field instanceof Map<?, ?> f && "vector".equals(f.get("type"))
					&& (f.get("name") == null || EMBEDDING_FIELD.equals(f.get("name")))
					&& f.get("dims") instanceof Number dims) {
				return dims.intValue();
			}
		}
		return null;
	}

	public void close() throws Exception {
		if (this.cluster != null) {
			this.cluster.close();
			logger.info("Connection with cluster closed");
		}
	}

	public record CouchbaseDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private String collectionName = DEFAULT_COLLECTION_NAME;

		private String scopeName = DEFAULT_SCOPE_NAME;

		private String bucketName = DEFAULT_BUCKET_NAME;

		private String vectorIndexName = DEFAULT_INDEX_NAME;

		private @Nullable Integer dimensions;

		private CouchbaseSimilarityFunction similarityFunction = CouchbaseSimilarityFunction.dot_product;

		private CouchbaseIndexOptimization indexOptimization = CouchbaseIndexOptimization.recall;

		private final Cluster cluster;

		private final CouchbaseAiSearchFilterExpressionConverter filterExpressionConverter = new CouchbaseAiSearchFilterExpressionConverter();

		private boolean initializeSchema = false;

		/**
		 * @throws IllegalArgumentException if couchbaseSearchVectorConfig or cluster is
		 * null
		 */
		private Builder(Cluster cluster, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(cluster, "Cluster must not be null");
			this.cluster = cluster;
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
		 * Configures the Couchbase collection storing {@link Document}.
		 * @param collectionName
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "Collection Name must not be empty");
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the Couchbase scope, parent of the selected collection. Search will
		 * be executed in this scope context.
		 * @param scopeName
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder scopeName(String scopeName) {
			Assert.hasText(scopeName, "Scope Name must not be empty");
			this.scopeName = scopeName;
			return this;
		}

		/**
		 * Configures the Couchbase bucket, parent of the selected Scope.
		 * @param bucketName
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder bucketName(String bucketName) {
			Assert.hasText(bucketName, "Bucket Name must not be empty");
			this.bucketName = bucketName;
			return this;
		}

		/**
		 * Configures the vector index name. This must match the name of the Vector Search
		 * Index Name in Atlas
		 * @param vectorIndexName
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder vectorIndexName(String vectorIndexName) {
			Assert.hasText(vectorIndexName, "Vector Index Name must not be empty");
			this.vectorIndexName = vectorIndexName;
			return this;
		}

		/**
		 * The number of dimensions in the vector.
		 * <p>
		 * If not set, the size is read from the existing search index. When the store
		 * creates the index, it asks the embedding model for the size. When set and the
		 * store initializes the schema, startup fails if an existing index has another
		 * size.
		 * @param dimensions
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder dimensions(Integer dimensions) {
			Assert.notNull(dimensions, "Dimensions must not be null");
			Assert.isTrue(dimensions > 0, "Dimensions must be greater than 0");
			this.dimensions = dimensions;
			return this;
		}

		/**
		 * Choose the method to calculate the similarity between the vector embedding in a
		 * Vector Search index and the vector embedding in a Vector Search query.
		 * @param similarityFunction
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder similarityFunction(CouchbaseSimilarityFunction similarityFunction) {
			Assert.notNull(similarityFunction, "Couchbase Similarity Function must not be null");
			this.similarityFunction = similarityFunction;
			return this;
		}

		/**
		 * Choose to prioritize accuracy or latency.
		 * @param indexOptimization
		 * @return this builder
		 */
		public CouchbaseSearchVectorStore.Builder indexOptimization(CouchbaseIndexOptimization indexOptimization) {
			Assert.notNull(indexOptimization, "Index Optimization must not be null");
			this.indexOptimization = indexOptimization;
			return this;
		}

		public CouchbaseSearchVectorStore build() {
			return new CouchbaseSearchVectorStore(this);
		}

	}

}
