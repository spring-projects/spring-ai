/*
 * Copyright 2023 - 2024 the original author or authors.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetrySpec;

import java.time.Duration;
import java.util.*;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
public class CouchbaseVectorStore extends AbstractObservationVectorStore implements InitializingBean, AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(CouchbaseVectorStore.class);

	private static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	private static final String DEFAULT_COLLECTION_NAME = "_default";

	private static final String DEFAULT_SCOPE_NAME = "_default";

	private static final String DEFAULT_BUCKET_NAME = "default";

	private final EmbeddingModel embeddingModel;

	private final Cluster cluster;

	private final CouchbaseAiSearchFilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	private final CouchbaseVectorStoreConfig config;

	private final Collection collection;

	private final Scope scope;

	private final Bucket bucket;

	protected CouchbaseVectorStore(Builder builder) {
		super(builder);

		Objects.requireNonNull(builder.cluster, "CouchbaseCluster must not be null");
		Objects.requireNonNull(builder.embeddingModel, "embeddingModel must not be null");
		this.initializeSchema = builder.initializeSchema;
		this.embeddingModel = builder.embeddingModel;
		this.config = builder.couchbaseVectorStoreConfig;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.cluster = builder.cluster;
		this.bucket = cluster.bucket(config.bucketName);
		this.scope = bucket.scope(config.scopeName);
		this.collection = scope.collection(config.collectionName);
	}

	@Override
	public void afterPropertiesSet() {

		if (!this.initializeSchema) {
			return;
		}

		try {
			initCluster();
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);
		for (Document document : documents) {
			CouchbaseDocument cbDoc = new CouchbaseDocument(document.getId(), document.getText(),
					document.getMetadata(), embeddings.get(documents.indexOf(document)));
			collection.upsert(document.getId(), cbDoc);
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		for (String id : idList) {
			collection.remove(id);
		}
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");
		try {
			String nativeFilter = this.filterExpressionConverter.convertExpression(filterExpression);
			String sql = String.format("DELETE FROM %s WHERE %s", collection.name(), nativeFilter);
			scope.query(sql, QueryOptions.queryOptions().metrics(true));
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(org.springframework.ai.vectorstore.SearchRequest springAiRequest) {
		float[] embeddings = this.embeddingModel.embed(springAiRequest.getQuery());
		int topK = springAiRequest.getTopK();

		double similarityThreshold = springAiRequest.getSimilarityThreshold();
		Filter.Expression fe = springAiRequest.getFilterExpression();

		String nativeFilterExpression = (fe != null) ? " AND " + this.filterExpressionConverter.convertExpression(fe)
				: "";
		String statement = String.format(
				"""
						SELECT c.* FROM `%s` AS c
						WHERE SEARCH(`c`, {"query": {"match_none": {}}, "knn": [{"field": "embedding", "k": %s, "vector": %s }   ]    }, {"index": "%s.%s.%s"}   )
						%s
						""",
				this.config.collectionName, topK, Arrays.toString(embeddings), this.config.bucketName,
				this.config.scopeName, this.config.vectorIndexName, nativeFilterExpression);

		QueryResult result = scope.query(statement, QueryOptions.queryOptions());

		return result.rowsAs(Document.class);
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this;
		return Optional.of(client);
	}

	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.COUCHBASE.value(), operationName)
			.collectionName(this.collection.name())
			.dimensions(this.embeddingModel.dimensions());
	}

	public static Builder builder(Cluster cluster, CouchbaseVectorStoreConfig couchbaseVectorStoreConfig,
			EmbeddingModel embeddingModel) {
		return new Builder(cluster, couchbaseVectorStoreConfig, embeddingModel);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final Cluster cluster;

		private final CouchbaseVectorStoreConfig couchbaseVectorStoreConfig;

		private final CouchbaseAiSearchFilterExpressionConverter filterExpressionConverter = new CouchbaseAiSearchFilterExpressionConverter();

		private boolean initializeSchema = false;

		/**
		 * @throws IllegalArgumentException if couchbaseVectorConfig or cluster is null
		 */
		private Builder(Cluster cluster, CouchbaseVectorStoreConfig couchbaseVectorStoreConfig,
				EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(cluster, "Cluster must not be null");
			this.cluster = cluster;
			Assert.notNull(couchbaseVectorStoreConfig, "CouchbaseVectorStoreConfig must not be NULL");
			this.couchbaseVectorStoreConfig = couchbaseVectorStoreConfig;
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

		public CouchbaseVectorStore build() {
			return new CouchbaseVectorStore(this);
		}

	}

	public void initCluster() throws InterruptedException {
		// init scope, collection, indexes
		BucketSettings bs = cluster.buckets().getAllBuckets().get(this.config.bucketName);
		if (bs == null) {
			cluster.buckets().createBucket(BucketSettings.create(this.config.bucketName));
		}
		Bucket b = cluster.bucket(this.config.bucketName);
		b.waitUntilReady(Duration.ofSeconds(1));
		boolean scopeExist = b.collections()
			.getAllScopes()
			.stream()
			.anyMatch(sc -> sc.name().equals(this.config.scopeName));
		if (!scopeExist) {
			b.collections().createScope(this.config.scopeName);
		}
		ConsistencyUtil.waitUntilScopePresent(cluster.core(), this.config.bucketName, this.config.scopeName);
		Scope s = b.scope(this.config.scopeName);
		boolean collectionExist = bucket.collections()
			.getAllScopes()
			.stream()
			.map(ScopeSpec::collections)
			.flatMap(java.util.Collection::stream)
			.filter(it -> it.scopeName().equals(this.config.scopeName))
			.map(CollectionSpec::name)
			.anyMatch(this.config.collectionName::equals);
		if (!collectionExist) {
			b.collections().createCollection(this.config.scopeName, this.config.collectionName);
			ConsistencyUtil.waitUntilCollectionPresent(cluster.core(), this.config.bucketName, this.config.scopeName,
					this.config.collectionName);
			Collection c = s.collection(this.config.collectionName);
			Mono.empty()
				.then(Mono.fromRunnable(
						() -> c.async()
							.queryIndexes()
							.createPrimaryIndex(CreatePrimaryQueryIndexOptions.createPrimaryQueryIndexOptions()
								.ignoreIfExists(true))))
				.retryWhen(RetrySpec.backoff(3, Duration.ofMillis(1000)));
		}

		boolean indexExist = s.searchIndexes()
			.getAllIndexes()
			.stream()
			.anyMatch(idx -> this.config.vectorIndexName.equals(idx.name()));
		if (!indexExist) {
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
			String jsonIndexValue = String.format(jsonIndexTemplate, this.config.vectorIndexName,
					this.config.bucketName, this.config.collectionName, this.config.scopeName,
					this.config.collectionName, this.config.dimensions, this.config.similarityFunction,
					this.config.indexOptimization);

			SearchIndex si = SearchIndex.fromJson(jsonIndexValue);
			s.searchIndexes().upsertIndex(si);
		}
	}

	public void close() throws Exception{
		if(this.cluster != null){
			this.cluster.close();
			logger.info("Connection with cluster closed");
		}
	}

	public static class CouchbaseVectorStoreConfig {

		private final String collectionName;

		private final String scopeName;

		private final String bucketName;

		private final String vectorIndexName;

		private final Integer dimensions;

		private final CouchbaseSimilarityFunction similarityFunction;

		private final CouchbaseIndexOptimization indexOptimization;

		private CouchbaseVectorStoreConfig(Builder builder) {
			this.bucketName = builder.bucketName;
			this.scopeName = builder.scopeName;
			this.collectionName = builder.collectionName;
			this.vectorIndexName = builder.vectorIndexName;
			this.dimensions = builder.dimensions;
			this.similarityFunction = builder.similarityFunction;
			this.indexOptimization = builder.indexOptimization;
		}

		public static CouchbaseVectorStoreConfig.Builder builder() {
			return new CouchbaseVectorStoreConfig.Builder();
		}

		public static CouchbaseVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		public static class Builder {

			private String collectionName = DEFAULT_COLLECTION_NAME;

			private String scopeName = DEFAULT_SCOPE_NAME;

			private String bucketName = DEFAULT_BUCKET_NAME;

			private String vectorIndexName = DEFAULT_INDEX_NAME;

			private Integer dimensions = 1536;

			private CouchbaseSimilarityFunction similarityFunction = CouchbaseSimilarityFunction.dot_product;

			private CouchbaseIndexOptimization indexOptimization = CouchbaseIndexOptimization.recall;

			private Builder() {
			}

			/**
			 * Configures the Couchbase collection storing {@link Document}.
			 * @param collectionName
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withCollectionName(String collectionName) {
				Assert.notNull(collectionName, "Collection Name must not be null");
				Assert.notNull(collectionName, "Collection Name must not be empty");
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Configures the Couchbase scope, parent of the selected collection. Search
			 * will be executed in this scope context.
			 * @param scopeName
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withScopeName(String scopeName) {
				Assert.notNull(scopeName, "Scope Name must not be null");
				Assert.notNull(scopeName, "Scope Name must not be empty");
				this.scopeName = scopeName;
				return this;
			}

			/**
			 * Configures the Couchbase bucket, parent of the selected Scope.
			 * @param bucketName
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withBucketName(String bucketName) {
				Assert.notNull(bucketName, "Bucket Name must not be null");
				Assert.notNull(bucketName, "Bucket Name must not be empty");
				this.bucketName = bucketName;
				return this;
			}

			/**
			 * Configures the vector index name. This must match the name of the Vector
			 * Search Index Name in Atlas
			 * @param vectorIndexName
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withVectorIndexName(String vectorIndexName) {
				Assert.notNull(vectorIndexName, "Vector Index Name must not be null");
				Assert.notNull(vectorIndexName, "Vector Index Name must not be empty");
				this.vectorIndexName = vectorIndexName;
				return this;
			}

			/**
			 * The number of dimensions in the vector.
			 * @param dimensions
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withDimensions(Integer dimensions) {
				Assert.notNull(dimensions, "Dimensions must not be null");
				Assert.notNull(dimensions, "Dimensions must not be empty");
				this.dimensions = dimensions;
				return this;
			}

			/**
			 * Choose the method to calculate the similarity between the vector embedding
			 * in a Vector Search index and the vector embedding in a Vector Search query.
			 * @param similarityFunction
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withSimilarityFunction(
					CouchbaseSimilarityFunction similarityFunction) {
				Assert.notNull(similarityFunction, "Couchbase Similarity Function must not be null");
				Assert.notNull(similarityFunction, "Couchbase Similarity Function must not be empty");
				this.similarityFunction = similarityFunction;
				return this;
			}

			/**
			 * Choose to prioritize accuracy or latency.
			 * @param indexOptimization
			 * @return this builder
			 */
			public CouchbaseVectorStoreConfig.Builder withIndexOptimization(
					CouchbaseIndexOptimization indexOptimization) {
				Assert.notNull(indexOptimization, "Index Optimization must not be null");
				Assert.notNull(indexOptimization, "Index Optimization must not be empty");
				this.indexOptimization = indexOptimization;
				return this;
			}

			public CouchbaseVectorStoreConfig build() {
				return new CouchbaseVectorStoreConfig(this);
			}

		}

	}

	public record CouchbaseDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

}
