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

package org.springframework.ai.vectorstore.milvus;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import io.milvus.param.collection.CollectionSchemaParam;
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
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Milvus implementation of the {@link org.springframework.ai.vectorstore.VectorStore}
 * interface. This implementation supports storing and searching document embeddings using
 * Milvus, an open-source vector database optimized for similarity search and AI
 * applications.
 *
 * <p>
 * Key features include:
 * <ul>
 * <li>Support for different similarity metrics (Cosine, L2, Inner Product)</li>
 * <li>Configurable index types for performance optimization</li>
 * <li>Metadata filtering capabilities</li>
 * <li>Automatic schema initialization</li>
 * <li>Batching strategy support for efficient operations</li>
 * </ul>
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a basic Milvus vector store
 * MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
 *     .initializeSchema(true)
 *     .build();
 *
 * // Create a customized Milvus vector store
 * MilvusVectorStore customVectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
 *     .databaseName("my_database")
 *     .collectionName("my_collection")
 *     .metricType(MetricType.COSINE)
 *     .indexType(IndexType.IVF_FLAT)
 *     .indexParameters("{\"nlist\":1024}")
 *     .embeddingDimension(1536)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .initializeSchema(true)
 *     .build();
 *
 * // Add documents to the store
 * List<Document> documents = List.of(
 *     new Document("content1", Map.of("meta1", "value1")),
 *     new Document("content2", Map.of("meta2", "value2"))
 * );
 * vectorStore.add(documents);
 *
 * // Perform similarity search
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.query("search text")
 *         .withTopK(5)
 *         .withSimilarityThreshold(0.7)
 *         .withFilterExpression("meta1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * The vector store supports various configuration options through its builder:
 * <ul>
 * <li>{@code milvusClient}: Required Milvus service client for database operations</li>
 * <li>{@code embeddingModel}: Required model for generating embeddings</li>
 * <li>{@code metricType}: Similarity metric (COSINE, L2, IP)</li>
 * <li>{@code indexType}: Type of index for search optimization</li>
 * <li>{@code databaseName}: Name of the Milvus database (default: "default")</li>
 * <li>{@code collectionName}: Name of the collection (default: "vector_store")</li>
 * <li>{@code initializeSchema}: Whether to automatically create the schema</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @see org.springframework.ai.vectorstore.VectorStore
 * @see io.milvus.client.MilvusServiceClient
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
	public static final String SIMILARITY_FIELD_NAME = "score";

	private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStore.class);

	private static final Map<MetricType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			MetricType.COSINE, VectorStoreSimilarityMetric.COSINE, MetricType.L2, VectorStoreSimilarityMetric.EUCLIDEAN,
			MetricType.IP, VectorStoreSimilarityMetric.DOT);

	public final FilterExpressionConverter filterExpressionConverter = new MilvusFilterExpressionConverter();

	private final MilvusServiceClient milvusClient;

	private final boolean initializeSchema;

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

	/**
	 * @param builder {@link VectorStore.Builder} for chroma vector store
	 */
	protected MilvusVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.milvusClient, "milvusClient must not be null");

		this.milvusClient = builder.milvusClient;
		this.initializeSchema = builder.initializeSchema;
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
	 * Creates a new MilvusBuilder instance with the specified Milvus client. This is the
	 * recommended way to instantiate a MilvusBuilder.
	 * @return a new MilvusBuilder instance
	 */
	public static Builder builder(MilvusServiceClient milvusServiceClient, EmbeddingModel embeddingModel) {
		return new Builder(milvusServiceClient, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {

		Assert.notNull(documents, "Documents must not be null");

		List<String> docIdArray = new ArrayList<>();
		List<String> contentArray = new ArrayList<>();
		List<JsonObject> metadataArray = new ArrayList<>();
		List<List<Float>> embeddingArray = new ArrayList<>();

		// TODO: Need to customize how we pass the embedding options
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		for (Document document : documents) {
			docIdArray.add(document.getId());
			// Use a (future) DocumentTextLayoutFormatter instance to extract
			// the content used to compute the embeddings
			contentArray.add(Objects.requireNonNullElse(document.getText(), ""));
			Gson gson = new Gson();
			String jsonString = gson.toJson(document.getMetadata());
			metadataArray.add(gson.fromJson(jsonString, JsonObject.class));
			embeddingArray.add(EmbeddingUtils.toList(embeddings.get(documents.indexOf(document))));
		}

		List<InsertParam.Field> fields = new ArrayList<>();
		// Insert ID field only if it is not auto ID
		if (!this.isAutoId) {
			fields.add(new InsertParam.Field(this.idFieldName, docIdArray));
		}
		fields.add(new InsertParam.Field(this.contentFieldName, contentArray));
		fields.add(new InsertParam.Field(this.metadataFieldName, metadataArray));
		fields.add(new InsertParam.Field(this.embeddingFieldName, embeddingArray));

		InsertParam insertParam = InsertParam.newBuilder()
			.withDatabaseName(this.databaseName)
			.withCollectionName(this.collectionName)
			.withFields(fields)
			.build();

		R<MutationResult> status = this.milvusClient.insert(insertParam);
		if (status.getException() != null) {
			throw new RuntimeException("Failed to insert:", status.getException());
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");

		String deleteExpression = String.format("%s in [%s]", this.idFieldName,
				idList.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",")));

		R<MutationResult> status = this.milvusClient.delete(DeleteParam.newBuilder()
			.withDatabaseName(this.databaseName)
			.withCollectionName(this.collectionName)
			.withExpr(deleteExpression)
			.build());

		long deleteCount = status.getData().getDeleteCnt();
		if (deleteCount != idList.size()) {
			logger.warn("Deleted only {} entries from requested {} ", deleteCount, idList.size());
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String nativeFilterExpression = this.filterExpressionConverter.convertExpression(filterExpression);

			R<MutationResult> status = this.milvusClient.delete(DeleteParam.newBuilder()
				.withDatabaseName(this.databaseName)
				.withCollectionName(this.collectionName)
				.withExpr(nativeFilterExpression)
				.build());

			if (status.getStatus() != Status.Success.getCode()) {
				throw new IllegalStateException("Failed to delete documents by filter: " + status.getMessage());
			}

			long deleteCount = status.getData().getDeleteCnt();
			logger.debug("Deleted {} documents matching filter expression", deleteCount);
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		String nativeFilterExpressions = "";
		String searchParamsJson = null;
		if (request instanceof MilvusSearchRequest milvusReq) {
			nativeFilterExpressions = StringUtils.hasText(milvusReq.getNativeExpression())
					? milvusReq.getNativeExpression() : getConvertedFilterExpression(request);

			searchParamsJson = StringUtils.hasText(milvusReq.getSearchParamsJson()) ? milvusReq.getSearchParamsJson()
					: null;
		}
		else {
			nativeFilterExpressions = getConvertedFilterExpression(request);
		}

		Assert.notNull(request.getQuery(), "Query string must not be null");
		List<String> outFieldNames = new ArrayList<>();
		outFieldNames.add(this.idFieldName);
		outFieldNames.add(this.contentFieldName);
		outFieldNames.add(this.metadataFieldName);
		float[] embedding = this.embeddingModel.embed(request.getQuery());

		var searchParamBuilder = SearchParam.newBuilder()
			.withDatabaseName(this.databaseName)
			.withCollectionName(this.collectionName)
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withMetricType(this.metricType)
			.withOutFields(outFieldNames)
			.withTopK(request.getTopK())
			.withVectors(List.of(EmbeddingUtils.toList(embedding)))
			.withVectorFieldName(this.embeddingFieldName);

		if (StringUtils.hasText(nativeFilterExpressions)) {
			searchParamBuilder.withExpr(nativeFilterExpressions);
		}

		if (StringUtils.hasText(searchParamsJson)) {
			searchParamBuilder.withParams(searchParamsJson);
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
				String docId = String.valueOf(rowRecord.get(this.idFieldName));
				String content = (String) rowRecord.get(this.contentFieldName);
				JsonObject metadata = null;
				try {
                    Object o = rowRecord.get(this.metadataFieldName);
					if (o instanceof JsonObject) {
                        metadata = (JsonObject) o;
                        // inject the distance into the metadata.
						metadata.addProperty(DocumentMetadata.DISTANCE.value(), 1 - getResultSimilarity(rowRecord));
					}
				catch (ParamException e) {
					// skip the ParamException if metadata doesn't exist for the custom
					// collection
				}
				Gson gson = new Gson();
				Type type = new TypeToken<Map<String, Object>>() {
				}.getType();
				return Document.builder()
					.id(docId)
					.text(content)
					.metadata((metadata != null) ? gson.fromJson(metadata, type) : Map.of())
					.score((double) getResultSimilarity(rowRecord))
					.build();
			})
			.toList();
	}

	private String getConvertedFilterExpression(SearchRequest request) {
		return (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";
	}

	private float getResultSimilarity(RowRecord rowRecord) {
		Float score = (Float) rowRecord.get(SIMILARITY_FIELD_NAME);
		return (this.metricType == MetricType.IP || this.metricType == MetricType.COSINE) ? score : (1 - score);
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
			this.milvusClient
				.releaseCollection(ReleaseCollectionParam.newBuilder().withCollectionName(this.collectionName).build());
		}
	}

	private boolean isDatabaseCollectionExists() {
		return this.milvusClient
			.hasCollection(HasCollectionParam.newBuilder()
				.withDatabaseName(this.databaseName)
				.withCollectionName(this.collectionName)
				.build())
			.getData();
	}

	// used by the test as well
	void createCollection() {

		if (!isDatabaseCollectionExists()) {
			createCollection(this.databaseName, this.collectionName, this.idFieldName, this.isAutoId,
					this.contentFieldName, this.metadataFieldName, this.embeddingFieldName);
			createIndex(this.databaseName, this.collectionName, this.embeddingFieldName, this.indexType,
					this.metricType, this.indexParameters);
		}

		R<DescribeIndexResponse> indexDescriptionResponse = this.milvusClient
			.describeIndex(DescribeIndexParam.newBuilder()
				.withDatabaseName(this.databaseName)
				.withCollectionName(this.collectionName)
				.build());

		if (indexDescriptionResponse.getData() == null) {
			createIndex(this.databaseName, this.collectionName, this.embeddingFieldName, this.indexType,
					this.metricType, this.indexParameters);
		}

		R<RpcStatus> loadCollectionStatus = this.milvusClient.loadCollection(LoadCollectionParam.newBuilder()
			.withDatabaseName(this.databaseName)
			.withCollectionName(this.collectionName)
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
			.withSchema(CollectionSchemaParam.newBuilder()
				.addFieldType(docIdFieldType)
				.addFieldType(contentFieldType)
				.addFieldType(metadataFieldType)
				.addFieldType(embeddingFieldType)
				.build())
			.build();

		R<RpcStatus> collectionStatus = this.milvusClient.createCollection(createCollectionReq);
		if (collectionStatus.getException() != null) {
			throw new RuntimeException("Failed to create collection", collectionStatus.getException());
		}

	}

	void createIndex(String databaseName, String collectionName, String embeddingFieldName, IndexType indexType,
			MetricType metricType, String indexParameters) {
		R<RpcStatus> indexStatus = this.milvusClient.createIndex(CreateIndexParam.newBuilder()
			.withDatabaseName(databaseName)
			.withCollectionName(collectionName)
			.withFieldName(embeddingFieldName)
			.withIndexType(indexType)
			.withMetricType(metricType)
			.withExtraParam(indexParameters)
			.withSyncMode(Boolean.FALSE)
			.build());

		if (indexStatus.getException() != null) {
			throw new RuntimeException("Failed to create Index", indexStatus.getException());
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
			logger.warn(
					"Failed to obtain the embedding dimensions from the embedding model and fall backs to default:{}",
					this.embeddingDimension, e);
		}
		return OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

	// used by the test as well
	void dropCollection() {

		R<RpcStatus> status = this.milvusClient
			.releaseCollection(ReleaseCollectionParam.newBuilder().withCollectionName(this.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Release collection failed!", status.getException());
		}

		status = this.milvusClient
			.dropIndex(DropIndexParam.newBuilder().withCollectionName(this.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Index failed!", status.getException());
		}

		status = this.milvusClient.dropCollection(DropCollectionParam.newBuilder()
			.withDatabaseName(this.databaseName)
			.withCollectionName(this.collectionName)
			.build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Collection failed!", status.getException());
		}
	}

	@Override
	public org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Builder createObservationContextBuilder(
			String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.MILVUS.value(), operationName)
			.collectionName(this.collectionName)
			.dimensions(this.embeddingModel.dimensions())
			.similarityMetric(getSimilarityMetric())
			.namespace(this.databaseName);
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.metricType)) {
			return this.metricType.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.metricType).value();
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.milvusClient;
		return Optional.of(client);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final MilvusServiceClient milvusClient;

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

		private boolean initializeSchema = false;

		/**
		 * @param milvusClient the Milvus service client to use for database operations
		 * @throws IllegalArgumentException if milvusClient is null
		 */
		private Builder(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(milvusClient, "milvusClient must not be null");
			this.milvusClient = milvusClient;
		}

		/**
		 * Configures the Milvus metric type to use for similarity calculations. See:
		 * https://milvus.io/docs/metric.md#floating for details on metric types.
		 * @param metricType the metric type to use (IP, L2, or COSINE)
		 * @return this builder instance
		 * @throws IllegalArgumentException if metricType is null or not one of IP, L2, or
		 * COSINE
		 */
		public Builder metricType(MetricType metricType) {
			Assert.notNull(metricType, "metricType must not be null");
			Assert.isTrue(metricType == MetricType.IP || metricType == MetricType.L2 || metricType == MetricType.COSINE,
					"Only the text metric types IP and L2 are supported");
			this.metricType = metricType;
			return this;
		}

		/**
		 * Configures the Milvus index type to use for vector search optimization.
		 * @param indexType the index type to use (defaults to IVF_FLAT if not specified)
		 * @return this builder instance
		 */
		public Builder indexType(IndexType indexType) {
			this.indexType = indexType;
			return this;
		}

		/**
		 * Configures the Milvus index parameters as a JSON string.
		 * @param indexParameters the index parameters to use (defaults to {"nlist":1024}
		 * if not specified)
		 * @return this builder instance
		 */
		public Builder indexParameters(String indexParameters) {
			this.indexParameters = indexParameters;
			return this;
		}

		/**
		 * Configures the Milvus database name.
		 * @param databaseName the database name to use (defaults to DEFAULT_DATABASE_NAME
		 * if not specified)
		 * @return this builder instance
		 */
		public Builder databaseName(String databaseName) {
			this.databaseName = databaseName;
			return this;
		}

		/**
		 * Configures the Milvus collection name.
		 * @param collectionName the collection name to use (defaults to
		 * DEFAULT_COLLECTION_NAME if not specified)
		 * @return this builder instance
		 */
		public Builder collectionName(String collectionName) {
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the dimension size of the embedding vectors.
		 * @param newEmbeddingDimension The dimension of the embedding (must be between 1
		 * and 32768)
		 * @return this builder instance
		 * @throws IllegalArgumentException if dimension is not between 1 and 32768
		 */
		public Builder embeddingDimension(int newEmbeddingDimension) {
			Assert.isTrue(newEmbeddingDimension >= 1 && newEmbeddingDimension <= 32768,
					"Dimension has to be withing the boundaries 1 and 32768 (inclusively)");
			this.embeddingDimension = newEmbeddingDimension;
			return this;
		}

		/**
		 * Configures the name of the field used for document IDs.
		 * @param idFieldName The name for the ID field (defaults to DOC_ID_FIELD_NAME)
		 * @return this builder instance
		 */
		public Builder iDFieldName(String idFieldName) {
			this.idFieldName = idFieldName;
			return this;
		}

		/**
		 * Configures whether to use auto-generated IDs for documents.
		 * @param isAutoId true to enable auto-generated IDs, false to use provided IDs
		 * @return this builder instance
		 */
		public Builder autoId(boolean isAutoId) {
			this.isAutoId = isAutoId;
			return this;
		}

		/**
		 * Configures the name of the field used for document content.
		 * @param contentFieldName The name for the content field (defaults to
		 * CONTENT_FIELD_NAME)
		 * @return this builder instance
		 */
		public Builder contentFieldName(String contentFieldName) {
			this.contentFieldName = contentFieldName;
			return this;
		}

		/**
		 * Configures the name of the field used for document metadata.
		 * @param metadataFieldName The name for the metadata field (defaults to
		 * METADATA_FIELD_NAME)
		 * @return this builder instance
		 */
		public Builder metadataFieldName(String metadataFieldName) {
			this.metadataFieldName = metadataFieldName;
			return this;
		}

		/**
		 * Configures the name of the field used for embedding vectors.
		 * @param embeddingFieldName The name for the embedding field (defaults to
		 * EMBEDDING_FIELD_NAME)
		 * @return this builder instance
		 */
		public Builder embeddingFieldName(String embeddingFieldName) {
			this.embeddingFieldName = embeddingFieldName;
			return this;
		}

		/**
		 * Configures whether to initialize the collection schema automatically.
		 * @param initializeSchema true to initialize schema automatically, false to use
		 * existing schema
		 * @return this builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Builds and returns a new MilvusVectorStore instance with the configured
		 * settings.
		 * @return a new MilvusVectorStore instance
		 * @throws IllegalStateException if the builder configuration is invalid
		 */
		public MilvusVectorStore build() {
			return new MilvusVectorStore(this);
		}

	}

}
