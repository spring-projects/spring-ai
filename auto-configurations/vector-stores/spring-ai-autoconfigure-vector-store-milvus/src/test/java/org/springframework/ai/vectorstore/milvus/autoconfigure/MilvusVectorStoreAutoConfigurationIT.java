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

package org.springframework.ai.vectorstore.milvus.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.param.partition.LoadPartitionsParam;
import io.milvus.response.QueryResultsWrapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Taewoong Kim
 */
@Testcontainers
public class MilvusVectorStoreAutoConfigurationIT {

	private static final String DEFAULT_PARTITION_NAME = "_default";

	@Container
	private static MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.6.18");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MilvusVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	public void addAndSearch() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.metric-type=COSINE",
					"spring.ai.vectorstore.milvus.index-type=IVF_FLAT",
					"spring.ai.vectorstore.milvus.embedding-dimension=384",
					"spring.ai.vectorstore.milvus.collection-name=myTestCollection",
					"spring.ai.vectorstore.milvus.initialize-schema=true",
					"spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

				vectorStore.add(this.documents);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.ADD);
				observationRegistry.clear();

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).hasSize(0);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();

			});
	}

	@Test
	public void searchWithCustomFields() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.metric-type=COSINE",
					"spring.ai.vectorstore.milvus.index-type=IVF_FLAT",
					"spring.ai.vectorstore.milvus.embedding-dimension=384",
					"spring.ai.vectorstore.milvus.collection-name=myCustomCollection",
					"spring.ai.vectorstore.milvus.id-field-name=identity",
					"spring.ai.vectorstore.milvus.content-field-name=text",
					"spring.ai.vectorstore.milvus.embedding-field-name=vectors",
					"spring.ai.vectorstore.milvus.metadata-field-name=meta",
					"spring.ai.vectorstore.milvus.initialize-schema=true",
					"spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

				vectorStore.add(this.documents);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.ADD);
				observationRegistry.clear();

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).hasSize(0);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.MILVUS,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();

			});
	}

	@Test
	public void addSearchAndDeleteWithPartitionName() {
		String collectionName = "partitionCollection" + UUID.randomUUID().toString().replace("-", "");
		String partitionName = "tenant_partition";
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.metric-type=COSINE",
					"spring.ai.vectorstore.milvus.index-type=IVF_FLAT",
					"spring.ai.vectorstore.milvus.embedding-dimension=384",
					"spring.ai.vectorstore.milvus.collection-name=" + collectionName,
					"spring.ai.vectorstore.milvus.partition-name=" + partitionName,
					"spring.ai.vectorstore.milvus.initialize-schema=true",
					"spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
			.run(context -> {
				MilvusServiceClient milvusClient = context.getBean(MilvusServiceClient.class);
				createAndLoadPartition(milvusClient, collectionName, partitionName);
				VectorStore vectorStore = context.getBean(VectorStore.class);
				VectorStore defaultPartitionVectorStore = MilvusVectorStore
					.builder(milvusClient, context.getBean(EmbeddingModel.class))
					.collectionName(collectionName)
					.build();
				Document partitionDocument = new Document("Spring AI partition support", Map.of("tenant", "a"));
				Document defaultPartitionDocument = new Document("Spring AI partition support",
						Map.of("tenant", "default"));

				defaultPartitionVectorStore.add(List.of(defaultPartitionDocument));
				vectorStore.add(List.of(partitionDocument));

				assertThat(countDocuments(milvusClient, collectionName, partitionName, partitionDocument.getId()))
					.isEqualTo(1);
				assertThat(
						countDocuments(milvusClient, collectionName, DEFAULT_PARTITION_NAME, partitionDocument.getId()))
					.isZero();
				assertThat(countDocuments(milvusClient, collectionName, DEFAULT_PARTITION_NAME,
						defaultPartitionDocument.getId()))
					.isEqualTo(1);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Spring AI partition support")
					.topK(5)
					.similarityThresholdAll()
					.build());
				assertThat(results).extracting(Document::getId)
					.contains(partitionDocument.getId())
					.doesNotContain(defaultPartitionDocument.getId());

				vectorStore.delete(List.of(partitionDocument.getId()));

				assertThat(countDocuments(milvusClient, collectionName, partitionName, partitionDocument.getId()))
					.isZero();
				assertThat(
						countDocuments(milvusClient, collectionName, DEFAULT_PARTITION_NAME, partitionDocument.getId()))
					.isZero();
				assertThat(countDocuments(milvusClient, collectionName, DEFAULT_PARTITION_NAME,
						defaultPartitionDocument.getId()))
					.isEqualTo(1);

				results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Spring AI partition support")
					.topK(5)
					.similarityThresholdAll()
					.build());
				assertThat(results).isEmpty();
			});
	}

	private long countDocuments(MilvusServiceClient milvusClient, String collectionName, String partitionName,
			String documentId) {
		var query = milvusClient.query(QueryParam.newBuilder()
			.withDatabaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
			.withCollectionName(collectionName)
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.addPartitionName(partitionName)
			.addOutField(MilvusVectorStore.DOC_ID_FIELD_NAME)
			.withExpr(MilvusVectorStore.DOC_ID_FIELD_NAME + " == \"" + documentId + "\"")
			.build());
		assertThat(query.getException()).isNull();
		return new QueryResultsWrapper(query.getData()).getRowCount();
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(MilvusVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MilvusVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
			.run(context -> {
				assertThat(context.getBeansOfType(MilvusVectorStoreProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
				assertThat(context.getBean(VectorStore.class)).isInstanceOf(MilvusVectorStore.class);
			});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsMilvus() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
			.withPropertyValues("spring.ai.vectorstore.type=milvus")
			.run(context -> {
				assertThat(context.getBeansOfType(MilvusVectorStoreProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
				assertThat(context.getBean(VectorStore.class)).isInstanceOf(MilvusVectorStore.class);
			});
	}

	private void createAndLoadPartition(MilvusServiceClient milvusClient, String collectionName, String partitionName) {
		var createPartition = milvusClient.createPartition(CreatePartitionParam.newBuilder()
			.withDatabaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
			.withCollectionName(collectionName)
			.withPartitionName(partitionName)
			.build());
		assertThat(createPartition.getException()).isNull();

		var loadPartitions = milvusClient.loadPartitions(LoadPartitionsParam.newBuilder()
			.withDatabaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
			.withCollectionName(collectionName)
			.addPartitionName(partitionName)
			.build());
		assertThat(loadPartitions.getException()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
