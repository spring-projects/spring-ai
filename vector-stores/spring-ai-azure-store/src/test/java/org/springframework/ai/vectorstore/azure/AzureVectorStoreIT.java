/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.azure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.azure.AzureVectorStore.MetadataField;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_ENDPOINT", matches = ".+")
public class AzureVectorStoreIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	private static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void addAndSearchTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build()), hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Hello").topK(1).build()),
						hasSize(0));
		});
	}

	@Test
	public void searchWithFilters() throws InterruptedException {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020, "activationDate", new Date(1000)));
			var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL", "activationDate", new Date(2000)));
			var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023, "activationDate", new Date(3000)));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(5).build()),
						hasSize(3));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG'")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' && year == 2020")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG']")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG','NL']")
				.build());

			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country not in ['BG']")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("NOT(country not in ['BG'])")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			// List<Document> results =
			// vectorStore.similaritySearch(SearchRequest.query("The World")
			// .withTopK(5)
			// .withSimilarityThresholdAll()
			// .withFilterExpression("activationDate > '1970-01-01T00:00:02Z'"));

			// assertThat(results).hasSize(1);
			// assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId(), bgDocument2.getId()));
		});
	}

	@Test
	public void documentUpdateTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			SearchRequest springSearchRequest = SearchRequest.builder().query("Spring").topK(5).build();

			Awaitility.await().until(() -> vectorStore.similaritySearch(springSearchRequest), hasSize(1));

			List<Document> results = vectorStore.similaritySearch(springSearchRequest);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			SearchRequest fooBarSearchRequest = SearchRequest.builder().query("FooBar").topK(5).build();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(fooBarSearchRequest).get(0).getText(),
						equalTo("The World is Big and Salvation Lurks Around the Corner"));

			results = vectorStore.similaritySearch(fooBarSearchRequest);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));
			Awaitility.await().until(() -> vectorStore.similaritySearch(fooBarSearchRequest), hasSize(0));

		});
	}

	@Test
	public void searchThresholdTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Depression").topK(50).similarityThresholdAll().build()),
						hasSize(3));

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Depression").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Depression")
				.topK(5)
				.similarityThreshold(similarityThreshold)
				.build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Hello").topK(1).build()),
						hasSize(0));
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			AzureVectorStore vectorStore = context.getBean(AzureVectorStore.class);
			Optional<SearchClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "AZURE_AI_SEARCH_INDEX_NAME", matches = ".+")
	void customFieldNamesTest() throws Exception {
		// Test with existing production index that uses custom field names
		String existingIndexName = System.getenv("AZURE_AI_SEARCH_INDEX_NAME");
		String endpoint = System.getenv("AZURE_AI_SEARCH_ENDPOINT");
		String apiKey = System.getenv("AZURE_AI_SEARCH_API_KEY");

		SearchIndexClient searchIndexClient = new SearchIndexClientBuilder().endpoint(endpoint)
			.credential(new AzureKeyCredential(apiKey))
			.buildClient();

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();

		// Create vector store with custom field names matching the production index
		// Index uses: chunk_text (content), embedding, metadata
		VectorStore vectorStore = AzureVectorStore.builder(searchIndexClient, embeddingModel)
			.indexName(existingIndexName)
			.initializeSchema(false) // Don't create - use existing index
			.contentFieldName("chunk_text") // Custom field name!
			.embeddingFieldName("embedding") // Standard name
			.metadataFieldName("metadata") // Standard name
			.build();

		// Trigger initialization
		((AzureVectorStore) vectorStore).afterPropertiesSet();

		// Search the existing index
		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("Azure Databricks").topK(3).build());

		// Verify we got results
		assertThat(results).isNotEmpty();
		assertThat(results.size()).isLessThanOrEqualTo(3);

		// Verify documents have content (from chunk_text field)
		Document firstDoc = results.get(0);
		assertThat(firstDoc.getId()).isNotNull();
		assertThat(firstDoc.getText()).isNotEmpty();
		assertThat(firstDoc.getScore()).isNotNull();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class Config {

		@Bean
		public SearchIndexClient searchIndexClient() {
			return new SearchIndexClientBuilder().endpoint(System.getenv("AZURE_AI_SEARCH_ENDPOINT"))
				.credential(new AzureKeyCredential(System.getenv("AZURE_AI_SEARCH_API_KEY")))
				.buildClient();
		}

		@Bean
		public VectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel) {
			return AzureVectorStore.builder(searchIndexClient, embeddingModel)
				.initializeSchema(true)
				.filterMetadataFields(List.of(MetadataField.text("country"), MetadataField.int64("year"),
						MetadataField.date("activationDate")))
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
