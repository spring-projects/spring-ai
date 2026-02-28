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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.ai.test.vectorstore.ObservationTestUtil.assertObservationRegistry;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_ENDPOINT", matches = ".+")
public class AzureVectorStoreAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.azure.apiKey=" + System.getenv("AZURE_AI_SEARCH_API_KEY"),
				"spring.ai.vectorstore.azure.url=" + System.getenv("AZURE_AI_SEARCH_ENDPOINT"))
		.withPropertyValues("spring.ai.vectorstore.azure.initialize-schema=true");

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

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

	@Test
	public void addAndSearchTest() {

		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.azure.initializeSchema=true",
					"spring.ai.vectorstore.azure.indexName=my_test_index", "spring.ai.vectorstore.azure.defaultTopK=6",
					"spring.ai.vectorstore.azure.defaultSimilarityThreshold=0.75")
			.run(context -> {

				var properties = context.getBean(AzureVectorStoreProperties.class);

				assertThat(properties.getUrl()).isEqualTo(System.getenv("AZURE_AI_SEARCH_ENDPOINT"));
				assertThat(properties.getApiKey()).isEqualTo(System.getenv("AZURE_AI_SEARCH_API_KEY"));
				assertThat(properties.getDefaultTopK()).isEqualTo(6);
				assertThat(properties.getDefaultSimilarityThreshold()).isEqualTo(0.75);
				assertThat(properties.getIndexName()).isEqualTo("my_test_index");

				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

				assertThat(vectorStore).isInstanceOf(AzureVectorStore.class);

				vectorStore.add(this.documents);

				Awaitility.await()
					.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build()),
							hasSize(1));

				assertObservationRegistry(observationRegistry, VectorStoreProvider.AZURE,
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

				assertObservationRegistry(observationRegistry, VectorStoreProvider.AZURE,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				Awaitility.await()
					.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build()),
							hasSize(0));

				assertObservationRegistry(observationRegistry, VectorStoreProvider.AZURE,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();

			});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(AzureVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(AzureVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(AzureVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(AzureVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsAzure() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=azure").run(context -> {
			assertThat(context.getBeansOfType(AzureVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(AzureVectorStore.class);
		});
	}

	@Test
	public void metadataFieldsAreBoundFromProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.type=azure",
					"spring.ai.vectorstore.azure.metadata-fields[0].name=department",
					"spring.ai.vectorstore.azure.metadata-fields[0].field-type=string",
					"spring.ai.vectorstore.azure.metadata-fields[1].name=year",
					"spring.ai.vectorstore.azure.metadata-fields[1].field-type=int64")
			.run(context -> {
				AzureVectorStoreProperties properties = context.getBean(AzureVectorStoreProperties.class);
				assertThat(properties.getMetadataFields()).hasSize(2);
				assertThat(properties.getMetadataFields().get(0).getName()).isEqualTo("department");
				assertThat(properties.getMetadataFields().get(0).getFieldType()).isEqualTo("string");
				assertThat(properties.getMetadataFields().get(1).getName()).isEqualTo("year");
				assertThat(properties.getMetadataFields().get(1).getFieldType()).isEqualTo("int64");

				VectorStore vectorStore = context.getBean(VectorStore.class);
				assertThat(vectorStore).isInstanceOf(AzureVectorStore.class);
			});
	}

	@Test
	public void metadataFiledsTypoAliasIsSupported() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.type=azure",
					"spring.ai.vectorstore.azure.metadata-fileds[0].name=filterField",
					"spring.ai.vectorstore.azure.metadata-fileds[0].fieldType=string")
			.run(context -> {
				AzureVectorStoreProperties properties = context.getBean(AzureVectorStoreProperties.class);
				assertThat(properties.getMetadataFields()).hasSize(1);
				assertThat(properties.getMetadataFields().get(0).getName()).isEqualTo("filterField");
				assertThat(properties.getMetadataFields().get(0).getFieldType()).isEqualTo("string");

				VectorStore vectorStore = context.getBean(VectorStore.class);
				assertThat(vectorStore).isInstanceOf(AzureVectorStore.class);
			});
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
