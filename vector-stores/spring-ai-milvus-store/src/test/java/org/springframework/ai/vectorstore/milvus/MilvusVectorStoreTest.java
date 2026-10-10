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

package org.springframework.ai.vectorstore.milvus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test class for {@link MilvusVectorStore}.
 *
 * @author waileong
 * @author Soby Chacko
 * @author Taewoong Kim
 */
@ExtendWith(MockitoExtension.class)
class MilvusVectorStoreTest {

	private static final String PARTITION_NAME = "tenant_partition";

	@Mock
	private MilvusServiceClient milvusClient;

	@Mock
	private EmbeddingModel embeddingModel;

	private MilvusVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel).build();
	}

	@Test
	void shouldPerformSimilaritySearchWithNativeExpression() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.nativeExpression("metadata[\"age\"] > 30") // this has higher priority
				.filterExpression("age <= 30") // this will be ignored
				.searchParamsJson("{\"nprobe\":128}")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);
			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo(request.getNativeExpression());
			assertThat(capturedParam.getParams()).isEqualTo(request.getSearchParamsJson());
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithFilterExpression() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			MilvusSearchRequest request = MilvusSearchRequest.milvusBuilder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30")
				.searchParamsJson("{\"nprobe\":128}")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo("metadata[\"age\"] > 30"); // filter
			assertThat(capturedParam.getParams()).isEqualTo(request.getSearchParamsJson());
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithFilterExpressionUsingCustomMetadataFieldName() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.metadataFieldName("meta")
			.build();

		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getExpr()).isEqualTo("meta[\"age\"] > 30");
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithOriginalSearchRequest() {
		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			String query = "sample query";
			SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getTopK()).isEqualTo(request.getTopK());
			assertThat(capturedParam.getExpr()).isEqualTo("metadata[\"age\"] > 30"); // filter
			assertThat(capturedParam.getParams()).isEqualTo("{}");
			assertThat(capturedParam.getPartitionNames()).isEmpty();
		}
	}

	@Test
	void shouldApplyPartitionNameWhenAddingDocuments() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.partitionName(PARTITION_NAME)
			.build();

		when(this.embeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 1.0f, 2.0f, 3.0f }));
		when(this.milvusClient.insert(any(InsertParam.class)))
			.thenReturn(R.success(MutationResult.getDefaultInstance()));

		this.vectorStore.doAdd(List.of(new Document("Spring AI partition test")));

		ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
		verify(this.milvusClient).insert(captor.capture());
		assertThat(captor.getValue().getPartitionName()).isEqualTo(PARTITION_NAME);
	}

	@Test
	void shouldIgnoreBlankPartitionNameWhenAddingDocuments() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel).partitionName(" ").build();

		when(this.embeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 1.0f, 2.0f, 3.0f }));
		when(this.milvusClient.insert(any(InsertParam.class)))
			.thenReturn(R.success(MutationResult.getDefaultInstance()));

		this.vectorStore.doAdd(List.of(new Document("Spring AI partition test")));

		ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
		verify(this.milvusClient).insert(captor.capture());
		assertThat(captor.getValue().getPartitionName()).isEmpty();
	}

	@Test
	void shouldApplyPartitionNameWhenDeletingByIdList() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.partitionName(PARTITION_NAME)
			.build();
		MutationResult mutationResult = MutationResult.newBuilder().setDeleteCnt(1).build();
		when(this.milvusClient.delete(any(DeleteParam.class))).thenReturn(R.success(mutationResult));

		this.vectorStore.doDelete(List.of("doc-1"));

		ArgumentCaptor<DeleteParam> captor = ArgumentCaptor.forClass(DeleteParam.class);
		verify(this.milvusClient).delete(captor.capture());
		assertThat(captor.getValue().getPartitionName()).isEqualTo(PARTITION_NAME);
	}

	@Test
	void shouldApplyPartitionNameWhenDeletingByFilterExpression() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.partitionName(PARTITION_NAME)
			.build();
		MutationResult mutationResult = MutationResult.newBuilder().setDeleteCnt(1).build();
		when(this.milvusClient.delete(any(DeleteParam.class))).thenReturn(R.success(mutationResult));

		this.vectorStore.delete("tenant == 'a'");

		ArgumentCaptor<DeleteParam> captor = ArgumentCaptor.forClass(DeleteParam.class);
		verify(this.milvusClient).delete(captor.capture());
		assertThat(captor.getValue().getPartitionName()).isEqualTo(PARTITION_NAME);
	}

	@Test
	void shouldApplyPartitionNameWhenPerformingSimilaritySearch() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.partitionName(PARTITION_NAME)
			.build();

		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			SearchRequest request = SearchRequest.builder().query("sample query").topK(5).build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getPartitionNames()).containsExactly(PARTITION_NAME);
		}
	}

	@Test
	void shouldPerformSimilaritySearchWithScalarMetadataFieldFilterExpression() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.metadataFields(MilvusVectorStore.MetadataField.int64("age"))
			.build();

		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of()))) {

			SearchRequest request = SearchRequest.builder()
				.query("sample query")
				.topK(5)
				.similarityThreshold(0.7)
				.filterExpression("age > 30 && country == 'NL'")
				.build();

			SearchParam capturedParam = performSimilaritySearch(mockedEmbeddingUtils, request);

			assertThat(capturedParam.getExpr()).isEqualTo("age > 30 && metadata[\"country\"] == \"NL\"");
		}
	}

	@Test
	void shouldInsertConfiguredScalarMetadataFields() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.metadataFields(MilvusVectorStore.MetadataField.int64("year"),
					MilvusVectorStore.MetadataField.text("category"), MilvusVectorStore.MetadataField.bool("published"))
			.build();

		when(this.embeddingModel.embed(any(), any(), any()))
			.thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }, new float[] { 0.4f, 0.5f, 0.6f }));
		when(this.milvusClient.insert(any(InsertParam.class)))
			.thenReturn(R.success(MutationResult.getDefaultInstance()));

		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class)) {
			mockedEmbeddingUtils.when(() -> EmbeddingUtils.toList(any())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

			this.vectorStore.doAdd(List.of(
					new Document("doc-1", "content one", Map.of("year", 2020, "category", "news", "published", true)),
					new Document("doc-2", "content two", Map.of("category", "blog", "published", false))));
		}

		ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
		verify(this.milvusClient).insert(captor.capture());

		List<InsertParam.Field> fields = captor.getValue().getFields();
		assertThat(field(fields, "year").getValues()).isEqualTo(Arrays.asList(2020L, null));
		assertThat(field(fields, "category").getValues()).isEqualTo(List.of("news", "blog"));
		assertThat(field(fields, "published").getValues()).isEqualTo(List.of(true, false));

		JsonObject metadata = (JsonObject) field(fields, MilvusVectorStore.METADATA_FIELD_NAME).getValues().get(0);
		assertThat(metadata.get("year").getAsInt()).isEqualTo(2020);
		assertThat(metadata.get("category").getAsString()).isEqualTo("news");
		assertThat(metadata.get("published").getAsBoolean()).isTrue();
	}

	@Test
	void shouldRejectFractionalValueForIntegerScalarMetadataField() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.metadataFields(MilvusVectorStore.MetadataField.int64("year"))
			.build();

		when(this.embeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.vectorStore.doAdd(List.of(new Document("doc-1", "content", Map.of("year", 2020.5)))))
			.withMessageContaining("integer value");
	}

	@Test
	void shouldRejectOutOfRangeValueForIntegerScalarMetadataField() {
		this.vectorStore = MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
			.metadataFields(new MilvusVectorStore.MetadataField("priority", DataType.Int8))
			.build();

		when(this.embeddingModel.embed(any(), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));

		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> this.vectorStore.doAdd(List.of(new Document("doc-1", "content", Map.of("priority", 128)))))
			.withMessageContaining("out of range");
	}

	@Test
	void shouldCreateScalarMetadataFieldsInCollectionSchema() {
		when(this.milvusClient.createCollection(any(CreateCollectionParam.class)))
			.thenReturn(R.success(new RpcStatus(RpcStatus.SUCCESS_MSG)));

		this.vectorStore.createCollection("default", "test_collection", "doc_id", false, "content", "metadata",
				"embedding", List.of(MilvusVectorStore.MetadataField.int64("year"),
						MilvusVectorStore.MetadataField.text("category")));

		ArgumentCaptor<CreateCollectionParam> captor = ArgumentCaptor.forClass(CreateCollectionParam.class);
		verify(this.milvusClient).createCollection(captor.capture());

		List<FieldType> fields = captor.getValue().getFieldTypes();
		FieldType yearField = fieldType(fields, "year");
		assertThat(yearField.getDataType()).isEqualTo(DataType.Int64);
		assertThat(yearField.isNullable()).isTrue();

		FieldType categoryField = fieldType(fields, "category");
		assertThat(categoryField.getDataType()).isEqualTo(DataType.VarChar);
		assertThat(categoryField.getMaxLength()).isEqualTo(65535);
		assertThat(categoryField.isNullable()).isTrue();
	}

	@Test
	void shouldRejectDuplicateScalarMetadataFieldNames() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
				.metadataFields(MilvusVectorStore.MetadataField.int64("year"),
						MilvusVectorStore.MetadataField.int32("year"))
				.build())
			.withMessageContaining("duplicate names");
	}

	@Test
	void shouldRejectScalarMetadataFieldNameConflict() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> MilvusVectorStore.builder(this.milvusClient, this.embeddingModel)
				.metadataFields(MilvusVectorStore.MetadataField.text(MilvusVectorStore.METADATA_FIELD_NAME))
				.build())
			.withMessageContaining("metadata field name");
	}

	@Test
	void shouldRejectUnsupportedScalarMetadataFieldType() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MilvusVectorStore.MetadataField("attributes", DataType.JSON))
			.withMessageContaining("JSON");
	}

	@Test
	void shouldPreserveMetadataIntegerNumberTypesInSearchResults() {
		long externalId = 10_000_000_000_000_001L;
		JsonObject metadata = new JsonObject();
		metadata.addProperty("external_id", externalId);
		metadata.addProperty("priority", 7);
		metadata.addProperty("weight", 1.5);

		RowRecord rowRecord = new RowRecord();
		rowRecord.put(MilvusVectorStore.DOC_ID_FIELD_NAME, "doc-1");
		rowRecord.put(MilvusVectorStore.CONTENT_FIELD_NAME, "content");
		rowRecord.put(MilvusVectorStore.METADATA_FIELD_NAME, metadata);
		rowRecord.put(MilvusVectorStore.SIMILARITY_FIELD_NAME, 0.75f);

		try (MockedStatic<EmbeddingUtils> mockedEmbeddingUtils = mockStatic(EmbeddingUtils.class);
				MockedConstruction<SearchResultsWrapper> mockedSearchResultsWrapper = mockConstruction(
						SearchResultsWrapper.class,
						(mock, context) -> when(mock.getRowRecords(0)).thenReturn(List.of(rowRecord)))) {

			List<Float> mockVector = List.of(1.0f, 2.0f, 3.0f);
			mockedEmbeddingUtils.when(() -> EmbeddingUtils.toList(any())).thenReturn(mockVector);

			SearchResults mockResults = mock(SearchResults.class);
			when(mockResults.getResults()).thenReturn(SearchResultData.getDefaultInstance());
			when(this.milvusClient.search(any(SearchParam.class))).thenReturn(R.success(mockResults));

			List<Document> results = this.vectorStore
				.doSimilaritySearch(SearchRequest.builder().query("sample query").build());

			assertThat(results).hasSize(1);
			Map<String, Object> resultMetadata = results.get(0).getMetadata();
			assertThat(resultMetadata.get("external_id")).isInstanceOf(Long.class).isEqualTo(externalId);
			assertThat(resultMetadata.get("priority")).isInstanceOf(Long.class).isEqualTo(7L);
			assertThat(resultMetadata.get("weight")).isInstanceOf(Double.class).isEqualTo(1.5);
			assertThat(resultMetadata.get(DocumentMetadata.DISTANCE.value())).isEqualTo(0.25);
		}
	}

	@Test
	void shouldEscapeIdsWhenDeletingByIdList() {
		MutationResult mutationResult = MutationResult.newBuilder().setDeleteCnt(3).build();
		when(this.milvusClient.delete(any(DeleteParam.class))).thenReturn(R.success(mutationResult));

		// Ids crafted to break out of a naive `'<id>'` quoting and inject filter syntax,
		// plus values containing backslash, double quote and newline.
		List<String> ids = List.of("plain-id", "x' || doc_id != 'x", "with\"dquote", "back\\slash\nnewline");

		this.vectorStore.doDelete(ids);

		ArgumentCaptor<DeleteParam> captor = ArgumentCaptor.forClass(DeleteParam.class);
		verify(this.milvusClient).delete(captor.capture());

		// Every id must be rendered as a JSON-escaped double-quoted literal, matching
		// the escaping used by MilvusFilterExpressionConverter for Filter.Expression
		// values.
		assertThat(captor.getValue().getExpr()).isEqualTo(
				"doc_id in [\"plain-id\",\"x' || doc_id != 'x\",\"with\\\"dquote\",\"back\\\\slash\\nnewline\"]");
		assertThat(captor.getValue().getPartitionName()).isEmpty();
	}

	private SearchParam performSimilaritySearch(MockedStatic<EmbeddingUtils> mockedEmbeddingUtils,
			SearchRequest request) {
		List<Float> mockVector = List.of(1.0f, 2.0f, 3.0f);
		mockedEmbeddingUtils.when(() -> EmbeddingUtils.toList(any())).thenReturn(mockVector);

		SearchResults mockResults = mock(SearchResults.class);
		when(mockResults.getResults()).thenReturn(SearchResultData.getDefaultInstance());

		R<SearchResults> mockResponse = R.success(mockResults);
		when(this.milvusClient.search(any(SearchParam.class))).thenReturn(mockResponse);

		ArgumentCaptor<SearchParam> searchParamCaptor = ArgumentCaptor.forClass(SearchParam.class);

		List<Document> results = this.vectorStore.doSimilaritySearch(request);

		assertThat(results).isNotNull();
		verify(this.milvusClient).search(searchParamCaptor.capture());
		return searchParamCaptor.getValue();
	}

	private InsertParam.Field field(List<InsertParam.Field> fields, String name) {
		return fields.stream().filter(field -> field.getName().equals(name)).findFirst().orElseThrow();
	}

	private FieldType fieldType(List<FieldType> fields, String name) {
		return fields.stream().filter(field -> field.getName().equals(name)).findFirst().orElseThrow();
	}

}
