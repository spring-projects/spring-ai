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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;

;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ElasticsearchVectorStoreObservationIT {

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

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

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(Config.class);
	}

	@BeforeEach
	void cleanDatabase() {
		getContextRunner().run(context -> {
			// deleting indices and data before following tests
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);
			List indices = elasticsearchClient.cat().indices().valueBody().stream().map(IndicesRecord::index).toList();
			if (!indices.isEmpty()) {
				elasticsearchClient.indices().delete(del -> del.index(indices));
			}
		});
	}

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		getContextRunner().run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("%s add".formatted(VectorStoreProvider.ELASTICSEARCH.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.ELASTICSEARCH.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(),
						"spring-ai-document-index")
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString(),
						VectorStoreSimilarityMetric.COSINE.value())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(
						HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString())

				.hasBeenStarted()
				.hasBeenStopped();

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("What is Great Depression").withSimilarityThresholdAll())
					.size(), greaterThan(1));

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

			assertThat(results).isNotEmpty();

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("%s query".formatted(VectorStoreProvider.ELASTICSEARCH.value()))
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.ELASTICSEARCH.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(),
						"What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "1536")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(),
						"spring-ai-document-index")
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_NAMESPACE.asString())
				.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_SEARCH_SIMILARITY_METRIC.asString(),
						VectorStoreSimilarityMetric.COSINE.value())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
						"0.0")

				.hasBeenStarted()
				.hasBeenStopped();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public ElasticsearchVectorStore vectorStoreDefault(EmbeddingModel embeddingModel, RestClient restClient,
				ObservationRegistry observationRegistry) {
			return new ElasticsearchVectorStore(new ElasticsearchVectorStoreOptions(), restClient, embeddingModel, true,
					observationRegistry, null, new TokenCountBatchingStrategy());
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

		@Bean
		RestClient restClient() {
			return RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();
		}

		@Bean
		ElasticsearchClient elasticsearchClient(RestClient restClient) {
			return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(
					new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))));
		}

	}

}
