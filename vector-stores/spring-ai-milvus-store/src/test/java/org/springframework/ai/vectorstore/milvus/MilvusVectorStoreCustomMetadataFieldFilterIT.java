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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.response.DescCollResponseWrapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for filtered similarity search using custom and scalar metadata
 * fields.
 *
 * @author Taewoong Kim
 * @author Soby Chacko
 */
@Testcontainers
class MilvusVectorStoreCustomMetadataFieldFilterIT {

	@Container
	private static final MilvusContainer milvusContainer = new MilvusContainer(MilvusImage.DEFAULT_IMAGE)
		.withEnv("DEPLOY_MODE", "STANDALONE");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void shouldFilterWithCustomMetadataFieldName() {
		this.contextRunner.run(context -> {
			MilvusVectorStore vectorStore = context.getBean(MilvusVectorStore.class);

			vectorStore.dropCollection();
			vectorStore.createCollection();

			Document matchingDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("age", 35, "country", "NL"));
			Document filteredDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("age", 20, "country", "BG"));
			vectorStore.add(List.of(matchingDocument, filteredDocument));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("age > 30")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(matchingDocument.getId());
		});
	}

	@Test
	void shouldFilterWithScalarMetadataField() {
		this.contextRunner.run(context -> {
			MilvusServiceClient milvusClient = context.getBean(MilvusServiceClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
			String collectionName = "test_scalar_metadata_filter_" + UUID.randomUUID().toString().replace("-", "_");
			MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusClient, embeddingModel)
				.collectionName(collectionName)
				.databaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
				.indexType(IndexType.IVF_FLAT)
				.metricType(MetricType.COSINE)
				.embeddingDimension(3)
				.metadataFields(MilvusVectorStore.MetadataField.int64("age"))
				.batchingStrategy(new TokenCountBatchingStrategy())
				.build();

			vectorStore.createCollection();

			try {
				Document matchingDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("age", 35, "country", "NL"));
				Document filteredByAgeDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("age", 20, "country", "NL"));
				Document filteredByCountryDocument = new Document(
						"The World is Big and Salvation Lurks Around the Corner", Map.of("age", 40, "country", "BG"));
				Document missingAgeDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				vectorStore.add(List.of(matchingDocument, filteredByAgeDocument, filteredByCountryDocument,
						missingAgeDocument));

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("age > 30 && country == 'NL'")
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(matchingDocument.getId());
			}
			finally {
				vectorStore.dropCollection();
			}
		});
	}

	@Test
	void shouldCreateInsertAndFilterAllSupportedScalarMetadataFieldTypes() {
		this.contextRunner.run(context -> {
			MilvusServiceClient milvusClient = context.getBean(MilvusServiceClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
			String collectionName = "test_scalar_metadata_types_" + UUID.randomUUID().toString().replace("-", "_");
			List<ScalarMetadataFieldCase> fieldCases = List.of(
					new ScalarMetadataFieldCase("bool_value", DataType.Bool, true, false),
					new ScalarMetadataFieldCase("int8_value", DataType.Int8, 8, 7),
					new ScalarMetadataFieldCase("int16_value", DataType.Int16, 1600, 1500),
					new ScalarMetadataFieldCase("int32_value", DataType.Int32, 320000, 310000),
					new ScalarMetadataFieldCase("int64_value", DataType.Int64, 6_400_000_000L, 6_300_000_000L),
					new ScalarMetadataFieldCase("float_value", DataType.Float, 1.25f, 1.0f),
					new ScalarMetadataFieldCase("double_value", DataType.Double, 2.5, 2.0),
					new ScalarMetadataFieldCase("text_value", DataType.VarChar, "match", "other"));
			List<MilvusVectorStore.MetadataField> metadataFields = fieldCases.stream()
				.map(ScalarMetadataFieldCase::metadataField)
				.toList();
			MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusClient, embeddingModel)
				.collectionName(collectionName)
				.databaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
				.indexType(IndexType.IVF_FLAT)
				.metricType(MetricType.COSINE)
				.embeddingDimension(3)
				.metadataFields(metadataFields)
				.batchingStrategy(new TokenCountBatchingStrategy())
				.build();

			vectorStore.createCollection();

			try {
				R<DescribeCollectionResponse> response = milvusClient
					.describeCollection(DescribeCollectionParam.newBuilder()
						.withDatabaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
						.withCollectionName(collectionName)
						.build());
				assertThat(response.getException()).isNull();
				DescCollResponseWrapper collection = new DescCollResponseWrapper(response.getData());
				for (MilvusVectorStore.MetadataField metadataField : metadataFields) {
					FieldType field = collection.getFieldByName(metadataField.name());
					assertThat(field).isNotNull();
					assertThat(field.getDataType()).isEqualTo(metadataField.fieldType());
					assertThat(field.isNullable()).isTrue();
				}

				Map<String, Object> matchingMetadata = fieldCases.stream()
					.collect(Collectors.toMap(field -> field.metadataField().name(),
							ScalarMetadataFieldCase::matchingValue));
				Map<String, Object> nonMatchingMetadata = fieldCases.stream()
					.collect(Collectors.toMap(field -> field.metadataField().name(),
							ScalarMetadataFieldCase::nonMatchingValue));
				Document matchingDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						matchingMetadata);
				Document filteredDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						nonMatchingMetadata);
				vectorStore.add(List.of(matchingDocument, filteredDocument));

				for (ScalarMetadataFieldCase field : fieldCases) {
					Filter.Expression filter = new Filter.Expression(Filter.ExpressionType.EQ,
							new Filter.Key(field.metadataField().name()), new Filter.Value(field.matchingValue()));
					assertFilterReturnsOnly(vectorStore, filter, matchingDocument);
				}
			}
			finally {
				vectorStore.dropCollection();
			}
		});
	}

	private static void assertFilterReturnsOnly(MilvusVectorStore vectorStore, Filter.Expression filterExpression,
			Document expectedDocument) {
		List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
			.query("The World")
			.topK(5)
			.similarityThresholdAll()
			.filterExpression(filterExpression)
			.build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(expectedDocument.getId());
	}

	private record ScalarMetadataFieldCase(MilvusVectorStore.MetadataField metadataField, Object matchingValue,
			Object nonMatchingValue) {

		private ScalarMetadataFieldCase(String name, DataType dataType, Object matchingValue, Object nonMatchingValue) {
			this(new MilvusVectorStore.MetadataField(name, dataType), matchingValue, nonMatchingValue);
		}

	}

	@SpringBootConfiguration
	static class TestApplication {

		@Bean
		MilvusServiceClient milvusClient() {
			return new MilvusServiceClient(ConnectParam.newBuilder()
				.withAuthorization("minioadmin", "minioadmin")
				.withUri(milvusContainer.getEndpoint())
				.build());
		}

		@Bean
		EmbeddingModel embeddingModel() {
			return new FixedVectorEmbeddingModel();
		}

		@Bean
		MilvusVectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
			return MilvusVectorStore.builder(milvusClient, embeddingModel)
				.collectionName("test_custom_metadata_filter")
				.databaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
				.indexType(IndexType.IVF_FLAT)
				.metricType(MetricType.COSINE)
				.embeddingDimension(3)
				.metadataFieldName("meta")
				.batchingStrategy(new TokenCountBatchingStrategy())
				.initializeSchema(true)
				.build();
		}

	}

	static final class FixedVectorEmbeddingModel implements EmbeddingModel {

		@Override
		public float[] embed(Document document) {
			return embed(document.getText());
		}

		@Override
		public float[] embed(String text) {
			return new float[] { 0.1f, 0.2f, 0.3f };
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				embeddings.add(new Embedding(new float[] { 0.1f, 0.2f, 0.3f }, i));
			}
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public int dimensions() {
			return 3;
		}

	}

}
