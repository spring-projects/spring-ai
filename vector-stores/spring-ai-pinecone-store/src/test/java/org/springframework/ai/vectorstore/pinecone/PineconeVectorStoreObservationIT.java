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

package org.springframework.ai.vectorstore.pinecone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
@Execution(ExecutionMode.SAME_THREAD)
public class PineconeVectorStoreObservationIT {

	private static final String PINECONE_INDEX_NAME = "spring-ai-test-index";

	// Default namespace (empty) so we never create new namespaces (serverless limit 100).
	// Cleared before each test. Set PINECONE_NAMESPACE env to override.
	private static String PINECONE_NAMESPACE;

	private static final String CUSTOM_CONTENT_FIELD_NAME = "article";

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

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	@BeforeEach
	public void setUpNamespace() {
		String env = System.getenv("PINECONE_NAMESPACE");
		// Default namespace (empty) so we never create a new namespace and hit serverless
		// limit (100). Cleared before each test. Set PINECONE_NAMESPACE to use a named
		// namespace instead.
		PINECONE_NAMESPACE = (env != null) ? env : "";
	}

	private static final String[] CLEAR_SEED_QUERIES = { "the", "world", "document", "content", "depression",
			"spring" };

	/**
	 * Clears the store's current namespace using only the VectorStore API (search then
	 * delete by ID in batches). Uses multiple seed queries so all documents are found.
	 * Waits until the namespace is empty (handles eventual consistency).
	 */
	private void clearCurrentNamespace(VectorStore vectorStore) {
		int topK = 10000;
		boolean anyDeleted;
		do {
			anyDeleted = false;
			for (String query : CLEAR_SEED_QUERIES) {
				List<Document> batch = vectorStore
					.similaritySearch(SearchRequest.builder().query(query).topK(topK).similarityThreshold(0f).build());
				if (!batch.isEmpty()) {
					vectorStore.delete(batch.stream().map(Document::getId).toList());
					anyDeleted = true;
				}
			}
		}
		while (anyDeleted);
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofMillis(500))
			.until(() -> vectorStore
				.similaritySearch(SearchRequest.builder().query("the").topK(1).similarityThreshold(0f).build()),
					hasSize(0));
	}

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			clearCurrentNamespace(vectorStore);
			observationRegistry.clear();

			vectorStore.add(this.documents);

			var addAssert = TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("%s add".formatted(VectorStoreProvider.PINECONE.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.PINECONE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), PINECONE_INDEX_NAME);
			if (StringUtils.hasText(PINECONE_NAMESPACE)) {
				addAssert = addAssert.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_NAMESPACE.asString(),
						PINECONE_NAMESPACE);
			}
			else {
				addAssert = addAssert
					.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString());
			}
			addAssert.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString(), "article")
				.doesNotHaveHighCardinalityKeyValueWithKey(
						HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(
						HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString())

				.hasBeenStarted()
				.hasBeenStopped();

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("What is Great Depression").topK(1).build()),
						hasSize(1));

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("What is Great Depression").topK(1).build());

			assertThat(results).isNotEmpty();

			var queryAssert = TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("%s query".formatted(VectorStoreProvider.PINECONE.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.PINECONE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(),
						"What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), PINECONE_INDEX_NAME);
			if (StringUtils.hasText(PINECONE_NAMESPACE)) {
				queryAssert = queryAssert.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_NAMESPACE.asString(),
						PINECONE_NAMESPACE);
			}
			else {
				queryAssert = queryAssert
					.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString());
			}
			queryAssert.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString(), "article")
				.doesNotHaveHighCardinalityKeyValueWithKey(
						HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
						"0.0")

				.hasBeenStarted()
				.hasBeenStopped();

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Hello").topK(1).build()),
						hasSize(0));

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
		public VectorStore vectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry) {
			return PineconeVectorStore.builder(embeddingModel)
				.apiKey(System.getenv("PINECONE_API_KEY"))
				.indexName(PINECONE_INDEX_NAME)
				.namespace(PINECONE_NAMESPACE)
				.contentFieldName(CUSTOM_CONTENT_FIELD_NAME)
				.observationRegistry(observationRegistry)
				.customObservationConvention(null)
				.batchingStrategy(new TokenCountBatchingStrategy())
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
