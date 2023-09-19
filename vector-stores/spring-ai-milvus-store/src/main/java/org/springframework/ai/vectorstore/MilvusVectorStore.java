/*
 * Copyright 2023-2023 the original author or authors.
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
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
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DropIndexParam;
import io.milvus.response.SearchResultsWrapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.context.SmartLifecycle;

/**
 * @author Christian Tzolov
 */
public class MilvusVectorStore implements VectorStore, SmartLifecycle {

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final String DOC_ID_FIELD_NAME = "doc_id";

	public static final String CONTENT_FIELD_NAME = "content";

	public static final String METADATA_FIELD_NAME = "metadata";

	public static final String EMBEDDING_FIELD_NAME = "embedding";

	public static final String DISTANCE_FIELD_NAME = "distance"; // automatically assigned
																	// by Milvus.

	public static final List<String> SEARCH_OUTPUT_FIELDS = Arrays.asList(DOC_ID_FIELD_NAME, CONTENT_FIELD_NAME,
			METADATA_FIELD_NAME);

	private final MilvusServiceClient milvusClient;

	private final EmbeddingClient embeddingClient;

	private final int dimensions;

	private final String collectionName;

	public MilvusVectorStore(MilvusServiceClient milvusClient, EmbeddingClient embeddingClient, String collectionName,
			int dimensions) {
		this.milvusClient = milvusClient;
		this.embeddingClient = embeddingClient;
		this.collectionName = collectionName;
		this.dimensions = dimensions;

		createCollection(); // TODO: should be started by the SmartLifecycle#start
	}

	public String getCollectionName() {
		return this.collectionName;
	}

	@Override
	public void add(List<Document> documents) {

		List<String> docIdArray = new ArrayList<>();
		List<String> contentArray = new ArrayList<>();
		List<JSONObject> metadataArray = new ArrayList<>();
		List<List<Float>> embeddingArray = new ArrayList<>();

		for (Document document : documents) {
			List<Double> embedding = this.embeddingClient.embed(document);

			docIdArray.add(document.getId());
			contentArray.add(document.getText());
			metadataArray.add(new JSONObject(document.getMetadata()));
			embeddingArray.add(toFloatList(embedding));
		}

		List<InsertParam.Field> fields = new ArrayList<>();
		fields.add(new InsertParam.Field(DOC_ID_FIELD_NAME, docIdArray));
		fields.add(new InsertParam.Field(CONTENT_FIELD_NAME, contentArray));
		fields.add(new InsertParam.Field(METADATA_FIELD_NAME, metadataArray));
		fields.add(new InsertParam.Field(EMBEDDING_FIELD_NAME, embeddingArray));

		InsertParam insertParam = InsertParam.newBuilder()
				.withCollectionName(this.collectionName)
				.withFields(fields)
				.build();

		R<MutationResult> status = this.milvusClient.insert(insertParam);
		if (status.getException() != null) {
			throw new RuntimeException("Failed to insert:", status.getException());
		}
		this.milvusClient.flush(FlushParam.newBuilder().addCollectionName(this.collectionName).build());
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		String deleteExpression = String.format("%s in [%s]", DOC_ID_FIELD_NAME,
				idList.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",")));

		R<MutationResult> status = this.milvusClient.delete(
				DeleteParam.newBuilder().withCollectionName(this.collectionName).withExpr(deleteExpression).build());

		long deleteCount = status.getData().getDeleteCnt();

		return Optional.of(status.getStatus() == Status.Success.getCode());
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return this.similaritySearch(query, 4);
	}

	@Override
	public List<Document> similaritySearch(String query, int topK) {
		return similaritySearch(query, topK, 1.0D);
	}

	@Override
	public List<Document> similaritySearch(String query, int topK, double threshold) {

		List<Double> embedding = this.embeddingClient.embed(query);

		SearchParam searchParam = SearchParam.newBuilder()
				.withCollectionName(this.collectionName)
				.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
				.withMetricType(MetricType.L2)
				.withOutFields(SEARCH_OUTPUT_FIELDS)
				.withTopK(topK)
				.withVectors(List.of(toFloatList(embedding)))
				.withVectorFieldName(EMBEDDING_FIELD_NAME)
				.build();

		R<SearchResults> respSearch = milvusClient.search(searchParam);

		if (respSearch.getException() != null) {
			throw new RuntimeException("Search failed!", respSearch.getException());
		}

		SearchResultsWrapper wrapperSearch = new SearchResultsWrapper(respSearch.getData().getResults());

		return wrapperSearch.getRowRecords()
				.stream()
				.filter(record -> ((Float) record.get(DISTANCE_FIELD_NAME)) < threshold)
				.map(record -> {
					String docId = (String) record.get(DOC_ID_FIELD_NAME);
					String content = (String) record.get(CONTENT_FIELD_NAME);
					JSONObject metadata = (JSONObject) record.get(METADATA_FIELD_NAME);
					return new Document(docId, content, metadata.getInnerMap());
				})
				.collect(Collectors.toList());
	}

	private List<Float> toFloatList(List<Double> embeddingDouble) {
		return embeddingDouble.stream().map(e -> e.floatValue()).collect(Collectors.toList());
	}

	// Smart Lifecycle
	@Override
	public void start() {
		System.out.println("HELLO WORLD!##################################");
		// if (!hasCollection(this.collectionName)) {
		// this.createCollection();
		// }

		// R<RpcStatus> s = this.milvusClient.loadCollection(
		// LoadCollectionParam.newBuilder()
		// .withCollectionName(this.collectionName)
		// .build());
	}

	// used by the test as well
	void dropCollection() {

		R<RpcStatus> status = this.milvusClient
				.releaseCollection(
						ReleaseCollectionParam.newBuilder().withCollectionName(this.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Release collection failed!", status.getException());
		}

		status = this.milvusClient
				.dropIndex(DropIndexParam.newBuilder().withCollectionName(this.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Index failed!", status.getException());
		}

		status = this.milvusClient
				.dropCollection(DropCollectionParam.newBuilder().withCollectionName(this.collectionName).build());

		if (status.getException() != null) {
			throw new RuntimeException("Drop Collection failed!", status.getException());
		}
	}

	// used by the test as well
	void createCollection() {

		if (!hasCollection(this.collectionName)) {

			FieldType docIdFieldType = FieldType.newBuilder()
					.withName(DOC_ID_FIELD_NAME)
					.withDataType(DataType.VarChar)
					.withMaxLength(36)
					.withPrimaryKey(true)
					.withAutoID(false)
					.build();
			FieldType contentFieldType = FieldType.newBuilder()
					.withName(CONTENT_FIELD_NAME)
					.withDataType(DataType.VarChar)
					.withMaxLength(65535)
					.build();
			FieldType metadataFieldType = FieldType.newBuilder()
					.withName(METADATA_FIELD_NAME)
					.withDataType(DataType.JSON)
					.build();
			FieldType embeddingFieldType = FieldType.newBuilder()
					.withName(EMBEDDING_FIELD_NAME)
					.withDataType(DataType.FloatVector)
					.withDimension(this.dimensions)
					.build();

			CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
					.withCollectionName(this.collectionName)
					// .withDatabaseName(this.collectionName)
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

		R<RpcStatus> indexStatus = this.milvusClient.createIndex(CreateIndexParam.newBuilder()
				.withCollectionName(this.collectionName)
				.withFieldName(EMBEDDING_FIELD_NAME)
				.withIndexType(IndexType.IVF_FLAT)
				.withMetricType(MetricType.L2)
				.withExtraParam("{\"nlist\":1024}")
				.withSyncMode(Boolean.FALSE)
				.build());

		if (indexStatus.getException() != null) {
			throw new RuntimeException("Failed to create Index", indexStatus.getException());
		}

		R<RpcStatus> loadCollectionStatus = this.milvusClient
				.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(this.collectionName).build());

		if (loadCollectionStatus.getException() != null) {
			throw new RuntimeException("Collection loading failed!", loadCollectionStatus.getException());
		}

	}

	@Override
	public void stop() {
		if (hasCollection(this.collectionName)) {
			this.milvusClient
					.releaseCollection(
							ReleaseCollectionParam.newBuilder().withCollectionName(this.collectionName).build());
		}
	}

	@Override
	public boolean isRunning() {
		return hasCollection(this.collectionName);
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	private boolean hasCollection(String collectionName) {
		return this.milvusClient
				.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collectionName).build())
				.getData();
	}

}