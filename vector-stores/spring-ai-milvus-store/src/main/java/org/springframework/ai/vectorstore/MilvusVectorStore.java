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

package org.springframework.ai.vectorstore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.exception.ParamException;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.R.Status;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.param.index.DropIndexParam;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public class MilvusVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	public static final String DEFAULT_DATABASE_NAME = "default";

	public static final String DEFAULT_COLLECTION_NAME = "vector_store";

	public static final String DOC_ID_FIELD_NAME = "doc_id";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String EMBEDDING_FIELD_NAME = "embedding";

	// Metadata, automatically assigned by Milvus.
	public static final String DISTANCE_FIELD_NAME = "distance";

	private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStore.class);

	private static Map<MetricType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(MetricType.COSINE,
			VectorStoreSimilarityMetric.COSINE, MetricType.L2, VectorStoreSimilarityMetric.EUCLIDEAN, MetricType.IP,
			VectorStoreSimilarityMetric.DOT);

	public final FilterExpressionConverter filterExpressionConverter = new MilvusFilterExpressionConverter();

	private final MilvusServiceClient milvusClient;

	private final EmbeddingModel embeddingModel;

	private final MilvusVectorStoreConfig config;

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	public MilvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		this(milvusClient, embeddingModel, MilvusVectorStoreConfig.defaultConfig(), initializeSchema,
				new TokenCountBatchingStrategy());
	}

	public MilvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel, boolean initializeSchema,
			BatchingStrategy batchingStrategy) {
		this(milvusClient, embeddingModel, MilvusVectorStoreConfig.defaultConfig(), initializeSchema, batchingStrategy);
	}

	public MilvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
			MilvusVectorStoreConfig config, boolean initializeSchema, BatchingStrategy batchingStrategy) {
		this(milvusClient, embeddingModel, config, initializeSchema, batchingStrategy, ObservationRegistry.NOOP, null);
	}

	public MilvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
			MilvusVectorStoreConfig config, boolean initializeSchema, BatchingStrategy batchingStrategy,
			ObservationRegistry observationRegistry, VectorStoreObservationConvention customObservationConvention) {

		super(observationRegistry, customObservationConvention);
		this.initializeSchema = initializeSchema;

		Assert.notNull(milvusClient, "MilvusServiceClient must not be null");
		Assert.notNull(milvusClient, "EmbeddingModel must not be null");

		this.milvusClient = milvusClient;
		this.embeddingModel = embeddingModel;
		this.config = config;
		this.batchingStrategy = batchingStrategy;
	}

	@Override
	public void doAdd(List<Document> documents) {

		Assert.notNull(documents, "Documents must not be null");

		List<String> docIdArray = new ArrayList<>();
		List<String> contentArray = new ArrayList<>();
		List<JSONObject> metadataArray = new ArrayList<>();
		List<List<Float>> embeddingArray = new ArrayList<>();

		// TODO: Need to customize how we pass the embedding options
		this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);

		for (Document document : documents) {
			docIdArray.add(document.getId());
			// Use a (future) DocumentTextLayoutFormatter instance to extract
			// the content used to compute the embeddings
			contentArray.add(document.getContent());
			metadataArray.add(new JSONObject(document.getMetadata()));
			embeddingArray.add(EmbeddingUtils.toList(document.getEmbedding()));
		}

		List<InsertParam.Field> fields = new ArrayList<>();
		// Insert ID field only if it is not auto ID
		if (!this.config.isAutoId) {
			fields.add(new InsertParam.Field(this.config.idFieldName, docIdArray));
		}
		fields.add(new InsertParam.Field(this.config.contentFieldName, contentArray));
		fields.add(new InsertParam.Field(this.config.metadataFieldName, metadataArray));
		fields.add(new InsertParam.Field(this.config.embeddingFieldName, embeddingArray));

		InsertParam insertParam = InsertParam.newBuilder()
			.withDatabaseName(this.config.databaseName)
			.withCollectionName(this.config.collectionName)
			.withFields(fields)
			.build();

		R<MutationResult> status = this.milvusClient.insert(insertParam);
		if (status.getException() != null) {
			throw new RuntimeException("Failed to insert:", status.getException());
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");

		String deleteExpression = String.format("%s in [%s]", this.config.idFieldName,
				idList.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",")));

		R<MutationResult> status = this.milvusClient.delete(DeleteParam.newBuilder()
			.withDatabaseName(this.config.databaseName)
			.withCollectionName(this.config.collectionName)
			.withExpr(deleteExpression)
			.build());

		long deleteCount = status.getData().getDeleteCnt();
		if (deleteCount != idList.size()) {
			logger.warn(String.format("Deleted only %s entries from requested %s ", deleteCount, idList.size()));
		}

		return Optional.of(status.getStatus() == Status.Success.getCode());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		String nativeFilterExpressions = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		Assert.notNull(request.getQuery(), "Query string must not be null");
		List<String> outFieldNames = new ArrayList<>();
		outFieldNames.add(this.config.idFieldName);
		outFieldNames.add(this.config.contentFieldName);
		outFieldNames.add(this.config.metadataFieldName);
		float[] embedding = this.embeddingModel.embed(request.getQuery());

		var searchParamBuilder = SearchParam.newBuilder()
			.withDatabaseName(this.config.databaseName)
			.withCollectionName(this.config.collectionName)
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withMetricType(this.config.metricType)
			.withOutFields(outFieldNames)
			.withTopK(request.getTopK())
			.withVectors(List.of(EmbeddingUtils.toList(embedding)))
			.withVectorFieldName(this.config.embeddingFieldName);

		if (StringUtils.hasText(nativeFilterExpressions)) {
			searchParamBuilder.withExpr(nativeFilterExpressions);
		}

		R<SearchResults> respSearch = this.milvusClient.search(searchParamBuilder.build());

		if (respSearch.getException() != null) {
			throw new RuntimeException("Search failed!", respSearch.getException());
		}

		SearchResultsWrapper wrapperSearch = new SearchResultsWrapper(respSearch.getData().getResults());

		return wrapperSearch.getRowRecords(0)
			.stream()
			.filter(rowRecord -> getResultSimilarity(rowRecord) >= request.getSimilarityThreshold())
			.map(rowRecord -> {
				String docId = String.valueOf(rowRecord.get(this.config.idFieldName));
				String content = (String) rowRecord.get(this.config.contentFieldName);
				JSONObject metadata = null;
				try {
					metadata = (JSONObject) rowRecord.get(this.config.metadataFieldName);
					// inject the distance into the metadata.
					metadata.put(DISTANCE_FIELD_NAME, 1 - getResultSimilarity(rowRecord));
				}
				catch (ParamException e) {
					// skip the ParamException if metadata doesn't exist for the custom
					// collection
				}
				return new Document(docId, content, (metadata != null) ? metadata.getInnerMap() : Map.of());
			})
			.toList();
	}

	private float getResultSimilarity(RowRecord rowRecord) {
		Float distance = (Float) rowRecord.get(DISTANCE_FIELD_NAME);
		return (this.config.metricType == MetricType.IP || this.config.metricType == MetricType.COSINE) ? distance
				: (1 - distance);
	}

	// ---------------------------------------------------------------------------------
	// Initialization
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() throws Exception {

		if (!this.initializeSchema) {
			return;
		}

		this.createCollection();
	}

	void releaseCollection() {
		if (isDatabaseCollectionExists()) {
			this.milvusClient.releaseCollection(
					ReleaseCollectionParam.newBuilder().withCollectionName(this.config.collectionName).build());
		}
	}

	private boolean isDatabaseCollectionExists() {
		return this.milvusClient
			.hasCollection(HasCollectionParam.newBuilder()
				.withDatabaseName(this.config.databaseName)
				.withCollectionName(this.config.collectionName)
				.build())
			.getData();
	}

	// used by the test as well
	void createCollection() {

		if (!isDatabaseCollectionExists()) {
			createCollection(this.config.databaseName, this.config.collectionName, this.config.idFieldName,
					this.config.isAutoId, this.config.contentFieldName, this.config.metadataFieldName,
					this.config.embeddingFieldName);
		}

		R<DescribeIndexResponse> indexDescriptionResponse = this.milvusClient
			.describeIndex(DescribeIndexParam.newBuilder()
				.withDatabaseName(this.config.databaseName)
				.withCollectionName(this.config.collectionName)
				.build());

		if (indexDescriptionResponse.getData() == null) {
			R<RpcStatus> indexStatus = this.milvusClient.createIndex(CreateIndexParam.newBuilder()
				.withDatabaseName(this.config.databaseName)
				.withCollectionName(this.config.collectionName)
				.withFieldName(this.config.embeddingFieldName)
				.withIndexType(this.config.indexType)
				.withMetricType(this.config.metricType)
				.withExtraParam(this.config.indexParameters)
				.withSyncMode(Boolean.FALSE)
				.build());

			if (indexStatus.getException() != null) {
				throw new RuntimeException("Failed to create Index", indexStatus.getException());
			}
		}

		R<RpcStatus> loadCollectionStatus = this.milvusClient.loadCollection(LoadCollectionParam.newBuilder()
			.withDatabaseName(this.config.databaseName)
			.withCollectionName(this.config.collectionName)
			.build());

		if (loadCollectionStatus.getException() != null) {
			throw new RuntimeException("Collection loading failed!", loadCollectionStatus.getException());
		}
	}

	void createCollection(String databaseName, String collectionName, String idFieldName, boolean isAutoId,
			String contentFieldName, String metadataFieldName, String embeddingFieldName) {
		FieldType docIdFieldType = FieldType.newBuilder()
			.withName(idFieldName)
			.withDataType(DataType.VarChar)
			.withMaxLength(36)
			.withPrimaryKey(true)
			.withAutoID(isAutoId)
			.build();
		FieldType contentFieldType = FieldType.newBuilder()
			.withName(contentFieldName)
			.withDataType(DataType.VarChar)
			.withMaxLength(65535)
			.build();
		FieldType metadataFieldType = FieldType.newBuilder()
			.withName(metadataFieldName)
			.withDataType(DataType.JSON)
			.build();
		FieldType embeddingFieldType = FieldType.newBuilder()
			.withName(embeddingFieldName)
			.withDataType(DataType.FloatVector)
			.withDimension(this.embeddingDimensions())
			.build();

		CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
			.withDatabaseName(databaseName)
			.withCollectionName(collectionName)
			.withDescription("Spring AI Vector Store")
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withShardsNum(2)
			.addFieldType(docIdFieldType)
			.addFieldType(contentFieldType)
			.addFieldType(metadataFieldType)
			.addFieldType(embeddingFieldType)
			.build();

		R<RpcStatus> collectionStatus = this.milvusClient.createCollection(createCollectionReq);
		if (collectionStatus.getException() != null) {
			throw new RuntimeException("Failed to create collection", collectionStatus.getException());
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

	// used by the test as well
	void dropCollection() {

		R<RpcStatus> status = this.milvusClient.releaseCollection(
				ReleaseCollectionParam.newBuilder().withCollectionName(this.config.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Release collection failed!", status.getException());
		}

		status = this.milvusClient
			.dropIndex(DropIndexParam.newBuilder().withCollectionName(this.config.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Index failed!", status.getException());
		}

		status = this.milvusClient.dropCollection(DropCollectionParam.newBuilder()
			.withDatabaseName(this.config.databaseName)
			.withCollectionName(this.config.collectionName)
			.build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Collection failed!", status.getException());
		}
	}

	@Override
	public org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Builder createObservationContextBuilder(
			String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.MILVUS.value(), operationName)
			.withCollectionName(this.config.collectionName)
			.withDimensions(this.embeddingModel.dimensions())
			.withSimilarityMetric(getSimilarityMetric())
			.withNamespace(this.config.databaseName);
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.config.metricType)) {
			return this.config.metricType.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.config.metricType).value();
	}

	/**
	 * Configuration for the Milvus vector store.
	 */
	public static final class MilvusVectorStoreConfig {

		private final String databaseName;

		private final String collectionName;

		private final int embeddingDimension;

		private final IndexType indexType;

		private final MetricType metricType;

		private final String indexParameters;

		private final String idFieldName;

		private final boolean isAutoId;

		private final String contentFieldName;

		private final String metadataFieldName;

		private final String embeddingFieldName;

		private MilvusVectorStoreConfig(Builder builder) {
			this.databaseName = builder.databaseName;
			this.collectionName = builder.collectionName;
			this.embeddingDimension = builder.embeddingDimension;
			this.indexType = builder.indexType;
			this.metricType = builder.metricType;
			this.indexParameters = builder.indexParameters;
			this.idFieldName = builder.idFieldName;
			this.isAutoId = builder.isAutoId;
			this.contentFieldName = builder.contentFieldName;
			this.metadataFieldName = builder.metadataFieldName;
			this.embeddingFieldName = builder.embeddingFieldName;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {

			return new Builder();
		}

		/**
		 * {@return the default config}
		 */
		public static MilvusVectorStoreConfig defaultConfig() {
			return builder().build();
		}

		public static final class Builder {

			private String databaseName = DEFAULT_DATABASE_NAME;

			private String collectionName = DEFAULT_COLLECTION_NAME;

			private int embeddingDimension = INVALID_EMBEDDING_DIMENSION;

			private IndexType indexType = IndexType.IVF_FLAT;

			private MetricType metricType = MetricType.COSINE;

			private String indexParameters = "{\"nlist\":1024}";

			private String idFieldName = DOC_ID_FIELD_NAME;

			private boolean isAutoId = false;

			private String contentFieldName = CONTENT_FIELD_NAME;

			private String metadataFieldName = METADATA_FIELD_NAME;

			private String embeddingFieldName = EMBEDDING_FIELD_NAME;

			private Builder() {
			}

			/**
			 * Configures the Milvus metric type to use. Leave {@literal null} or blank to
			 * use the metric metric: https://milvus.io/docs/metric.md#floating
			 * @param metricType the metric type to use
			 * @return this builder
			 */
			public Builder withMetricType(MetricType metricType) {
				Assert.notNull(metricType, "Collection Name must not be empty");
				Assert.isTrue(
						metricType == MetricType.IP || metricType == MetricType.L2 || metricType == MetricType.COSINE,
						"Only the text metric types IP and L2 are supported");

				this.metricType = metricType;
				return this;
			}

			/**
			 * Configures the Milvus index type to use. Leave {@literal null} or blank to
			 * use the default index.
			 * @param indexType the index type to use
			 * @return this builder
			 */
			public Builder withIndexType(IndexType indexType) {
				this.indexType = indexType;
				return this;
			}

			/**
			 * Configures the Milvus index parameters to use. Leave {@literal null} or
			 * blank to use the default index parameters.
			 * @param indexParameters the index parameters to use
			 * @return this builder
			 */
			public Builder withIndexParameters(String indexParameters) {
				this.indexParameters = indexParameters;
				return this;
			}

			/**
			 * Configures the Milvus database name to use. Leave {@literal null} or blank
			 * to use the default database.
			 * @param databaseName the database name to use
			 * @return this builder
			 */
			public Builder withDatabaseName(String databaseName) {
				this.databaseName = databaseName;
				return this;
			}

			/**
			 * Configures the Milvus collection name to use. Leave {@literal null} or
			 * blank to use the default collection name.
			 * @param collectionName the collection name to use
			 * @return this builder
			 */
			public Builder withCollectionName(String collectionName) {
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Configures the size of the embedding. Defaults to {@literal 1536}, inline
			 * with OpenAIs embeddings.
			 * @param newEmbeddingDimension The dimension of the embedding
			 * @return this builder
			 */
			public Builder withEmbeddingDimension(int newEmbeddingDimension) {

				Assert.isTrue(newEmbeddingDimension >= 1 && newEmbeddingDimension <= 32768,
						"Dimension has to be withing the boundaries 1 and 32768 (inclusively)");

				this.embeddingDimension = newEmbeddingDimension;
				return this;
			}

			/**
			 * Configures the ID field name. Default is {@value #DOC_ID_FIELD_NAME}.
			 * @param idFieldName The name for the ID field
			 * @return this builder
			 */
			public Builder withIDFieldName(String idFieldName) {
				this.idFieldName = idFieldName;
				return this;
			}

			/**
			 * Configures the boolean flag if the auto-id is used. Default is false.
			 * @param isAutoId boolean flag to indicate if the auto-id is enabled
			 * @return this builder
			 */
			public Builder withAutoId(boolean isAutoId) {
				this.isAutoId = isAutoId;
				return this;
			}

			/**
			 * Configures the content field name. Default is {@value #CONTENT_FIELD_NAME}.
			 * @param contentFieldName The name for the content field
			 * @return this builder
			 */
			public Builder withContentFieldName(String contentFieldName) {
				this.contentFieldName = contentFieldName;
				return this;
			}

			/**
			 * Configures the metadata field name. Default is
			 * {@value #METADATA_FIELD_NAME}.
			 * @param metadataFieldName The name for the metadata field
			 * @return this builder
			 */
			public Builder withMetadataFieldName(String metadataFieldName) {
				this.metadataFieldName = metadataFieldName;
				return this;
			}

			/**
			 * Configures the embedding field name. Default is
			 * {@value #EMBEDDING_FIELD_NAME}.
			 * @param embeddingFieldName The name for the embedding field
			 * @return this builder
			 */
			public Builder withEmbeddingFieldName(String embeddingFieldName) {
				this.embeddingFieldName = embeddingFieldName;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 */
			public MilvusVectorStoreConfig build() {
				return new MilvusVectorStoreConfig(this);
			}

		}

	}

}
