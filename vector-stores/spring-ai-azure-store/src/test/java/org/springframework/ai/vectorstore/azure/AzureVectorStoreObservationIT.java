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
package org.springframework.ai.vectorstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.azure.AzureVectorStore.MetadataField;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

/**
 * Integration tests for observation instrumentation AbstractObservationVectorStore in
 * {@link AzureVectorStore}.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_ENDPOINT", matches = ".+")
public class AzureVectorStoreObservationIT {

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

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
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
				.hasContextualNameEqualTo("vector_store azure add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(), "azure")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "none")
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
				.hasContextualNameEqualTo("vector_store azure query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(), "azure")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(), "vector_store")

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.QUERY.asString(), "What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DIMENSIONS.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.NAMESPACE.asString(), "none")
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
		public SearchIndexClient searchIndexClient() {
			return new SearchIndexClientBuilder().endpoint(System.getenv("AZURE_AI_SEARCH_ENDPOINT"))
				.credential(new AzureKeyCredential(System.getenv("AZURE_AI_SEARCH_API_KEY")))
				.buildClient();
		}

		@Bean
		public VectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel,
				ObservationRegistry observationRegistry) {
			var filterableMetaFields = List.of(MetadataField.text("country"), MetadataField.int64("year"),
					MetadataField.date("activationDate"));
			return new AzureVectorStore(searchIndexClient, embeddingModel, true, filterableMetaFields,
					observationRegistry, null);
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
