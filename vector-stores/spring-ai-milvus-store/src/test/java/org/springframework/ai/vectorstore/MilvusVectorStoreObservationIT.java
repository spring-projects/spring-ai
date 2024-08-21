/*
 * Copyright 2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;

/**
 * @author Christian Tzolov
 */
@Testcontainers
public class MilvusVectorStoreObservationIT {

	@Container
	private static MilvusContainer milvusContainer = new MilvusContainer("milvusdb/milvus:v2.3.8");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store milvus add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.MILVUS.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "test_vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "default")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_THRESHOLD.asString(), "none")

				.hasBeenStarted()
				.hasBeenStopped();

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

			assertThat(results).isNotEmpty();

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("vector_store milvus query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.MILVUS.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "test_vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "default")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.FIELD_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "cosine")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.SIMILARITY_THRESHOLD.asString(), "0.0")

				.hasBeenStarted()
				.hasBeenStopped();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
				ObservationRegistry observationRegistry) {
			MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder()
				.withCollectionName("test_vector_store")
				.withDatabaseName("default")
				.withIndexType(IndexType.IVF_FLAT)
				.withMetricType(MetricType.COSINE)
				.build();
			return new MilvusVectorStore(milvusClient, embeddingModel, config, true, new TokenCountBatchingStrategy(),
					observationRegistry, null);
		}

		@Bean
		public MilvusServiceClient milvusClient() {
			return new MilvusServiceClient(ConnectParam.newBuilder()
				.withAuthorization("minioadmin", "minioadmin")
				.withUri(milvusContainer.getEndpoint())
				.build());
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
