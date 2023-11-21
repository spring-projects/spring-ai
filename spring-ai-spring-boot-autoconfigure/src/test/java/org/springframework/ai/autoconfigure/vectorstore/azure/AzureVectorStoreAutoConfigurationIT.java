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

package org.springframework.ai.autoconfigure.vectorstore.azure;

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
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.AzureVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_ENDPOINT", matches = ".+")
public class AzureVectorStoreAutoConfigurationIT {

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

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.azure.apiKey=" + System.getenv("AZURE_AI_SEARCH_API_KEY"),
				"spring.ai.vectorstore.azure.url=" + System.getenv("AZURE_AI_SEARCH_ENDPOINT"));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	@Test
	public void addAndSearchTest() {

		contextRunner
			.withPropertyValues("spring.ai.vectorstore.azure.indexName=my_test_index",
					"spring.ai.vectorstore.azure.defaultTopK=6",
					"spring.ai.vectorstore.azure.defaultSimilarityThreshold=0.75")
			.run(context -> {

				var properties = context.getBean(AzureVectorStoreProperties.class);

				assertThat(properties.getUrl()).isEqualTo(System.getenv("AZURE_AI_SEARCH_ENDPOINT"));
				assertThat(properties.getApiKey()).isEqualTo(System.getenv("AZURE_AI_SEARCH_API_KEY"));
				assertThat(properties.getDefaultTopK()).isEqualTo(6);
				assertThat(properties.getDefaultSimilarityThreshold()).isEqualTo(0.75);
				assertThat(properties.getIndexName()).isEqualTo("my_test_index");

				VectorStore vectorStore = context.getBean(VectorStore.class);

				assertThat(vectorStore).isInstanceOf(AzureVectorStore.class);

				vectorStore.add(documents);

				Awaitility.await().until(() -> {
					return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				}, hasSize(1));

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				// Remove all documents from the store
				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				Awaitility.await().until(() -> {
					return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				}, hasSize(0));
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingClient embeddingClient() {
			return new TransformersEmbeddingClient();
		}

	}

}
