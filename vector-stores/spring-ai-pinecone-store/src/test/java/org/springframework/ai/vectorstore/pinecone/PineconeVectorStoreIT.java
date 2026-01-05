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

package org.springframework.ai.vectorstore.pinecone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.pinecone.clients.Pinecone;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
@Execution(ExecutionMode.SAME_THREAD)
public class PineconeVectorStoreIT extends BaseVectorStoreTests {

	private static final String PINECONE_INDEX_NAME = "spring-ai-test-index";

	// Default namespace (empty) so we never create new namespaces (serverless limit 100).
	// Cleared before each test. Set PINECONE_NAMESPACE env to override.
	private static String PINECONE_NAMESPACE;

	private static final String CUSTOM_CONTENT_FIELD_NAME = "article";

	private static final int DEFAULT_TOP_K = 50;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

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
		// Serverless index has high write/read consistency latency; use 2 min for
		// add/delete
		Awaitility.setDefaultTimeout(Duration.ofMinutes(2));
	}

	@BeforeEach
	public void setUpNamespace() {
		String env = System.getenv("PINECONE_NAMESPACE");
		// Default namespace (empty) so we never create a new namespace and hit serverless
		// limit (100). Cleared before each test. Set PINECONE_NAMESPACE to use a named
		// namespace instead.
		PINECONE_NAMESPACE = (env != null) ? env : "";
	}

	/**
	 * Seed queries used to sweep the namespace when clearing. Empty-string query often
	 * does not match all vectors, so we use several diverse queries to find and delete
	 * every document.
	 */
	private static final String[] CLEAR_SEED_QUERIES = { "the", "world", "document", "content", "depression",
			"spring" };

	/**
	 * Clears the store's current namespace using only the VectorStore API (search then
	 * delete by ID in batches). Uses multiple seed queries so all documents are found
	 * (empty query does not match every vector). Waits until the namespace is empty
	 * (handles eventual consistency). Use with SAME_THREAD execution so parallel tests do
	 * not pollute.
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

	@Override
	protected Duration getVerificationTimeout() {
		return Duration.ofSeconds(90);
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			clearCurrentNamespace(vectorStore);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	public void addAndSearchTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			clearCurrentNamespace(vectorStore);

			vectorStore.add(this.documents);

			List<Document> results = Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(1).build()), hasSize(1));

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
	public void addAndSearchWithFilters() {

		// Pinecone metadata filtering syntax:
		// https://docs.pinecone.io/docs/metadata-filtering

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			clearCurrentNamespace(vectorStore);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			SearchRequest searchRequest = SearchRequest.builder().query("The World").build();

			List<Document> results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.from(searchRequest).topK(5).build()),
						hasSize(2));
			assertThat(results).hasSize(2);

			results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.from(searchRequest)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'Bulgaria'")
					.build()), hasSize(1));
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.from(searchRequest)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'Netherlands'")
					.build()), hasSize(1));
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.from(searchRequest)
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("NOT(country == 'Netherlands')")
					.build()), hasSize(1));
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.from(searchRequest).topK(1).build()),
						hasSize(0));
		});
	}

	@Test
	public void documentUpdateTest() {

		// Note ,using OpenAI to calculate embeddings
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			clearCurrentNamespace(vectorStore);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			SearchRequest springSearchRequest = SearchRequest.builder().query("Spring").topK(5).build();

			List<Document> results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(springSearchRequest), hasSize(1));
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

			results = Awaitility.await().until(() -> vectorStore.similaritySearch(fooBarSearchRequest), hasSize(1));
			assertThat(results.get(0).getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
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
			clearCurrentNamespace(vectorStore);

			vectorStore.add(this.documents);

			List<Document> fullResult = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Depression").topK(50).similarityThresholdAll().build()),
						hasSize(3));
			assertThat(fullResult).hasSize(3);

			// Sort by score descending so threshold is between true top two
			List<Double> scores = fullResult.stream()
				.map(Document::getScore)
				.sorted((a, b) -> Double.compare(b, a))
				.toList();

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder()
					.query("Depression")
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build()), hasSize(1));
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
	void deleteWithComplexFilterExpression() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			clearCurrentNamespace(vectorStore);

			cleanupExistingDocuments(vectorStore, "Content");

			var documents = createContentDocuments();
			vectorStore.add(documents);

			awaitDocumentsCount(vectorStore, "Content", 3);

			Filter.Expression complexFilter = createComplexFilter();
			vectorStore.delete(complexFilter);

			List<Document> results = Awaitility.await()
				.atMost(Duration.ofMinutes(3))
				.pollInterval(Duration.ofSeconds(2))
				.until(() -> searchDocuments(vectorStore, "Content", 5), hasSize(2));
			assertThat(results).hasSize(2);
			assertComplexFilterResults(results);

			vectorStore.delete(List.of(documents.get(0).getId(), documents.get(2).getId())); // doc1
																								// and
																								// doc3
			awaitDocumentsCount(vectorStore, "Content", 0);
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			PineconeVectorStore vectorStore = context.getBean(PineconeVectorStore.class);
			Optional<Pinecone> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	private void cleanupExistingDocuments(VectorStore vectorStore, String query) {
		List<Document> existingDocs = searchDocuments(vectorStore, query, DEFAULT_TOP_K);
		if (!existingDocs.isEmpty()) {
			vectorStore.delete(existingDocs.stream().map(Document::getId).toList());
		}
		awaitDocumentsCount(vectorStore, query, 0);
	}

	private List<Document> createWorldDocuments() {
		return List.of(
				new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020)),
				new Document("The World is Big and Salvation Lurks Around the Corner", Map.of("country", "NL")),
				new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023)));
	}

	private List<Document> createContentDocuments() {
		return List.of(new Document("Content 1", Map.of("type", "A", "priority", 1)),
				new Document("Content 2", Map.of("type", "A", "priority", 2)),
				new Document("Content 3", Map.of("type", "B", "priority", 1)));
	}

	private Filter.Expression createComplexFilter() {
		Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT, new Filter.Key("priority"),
				new Filter.Value(1));
		Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
				new Filter.Value("A"));
		return new Filter.Expression(Filter.ExpressionType.AND, typeFilter, priorityFilter);
	}

	private void assertComplexFilterResults(List<Document> results) {
		assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
			.containsExactlyInAnyOrder("A", "B");
		assertThat(results.stream()
			.map(doc -> ((Number) doc.getMetadata().get("priority")).intValue())
			.collect(Collectors.toList())).containsExactlyInAnyOrder(1, 1);
	}

	private List<Document> searchDocuments(VectorStore vectorStore, String query, int topK) {
		return vectorStore
			.similaritySearch(SearchRequest.builder().query(query).topK(topK).similarityThresholdAll().build());
	}

	private void awaitDocumentsCount(VectorStore vectorStore, String query, int expectedCount) {
		Awaitility.await().until(() -> searchDocuments(vectorStore, query, DEFAULT_TOP_K), hasSize(expectedCount));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(EmbeddingModel embeddingModel) {
			String apikey = System.getenv("PINECONE_API_KEY");

			return PineconeVectorStore.builder(embeddingModel)
				.apiKey(apikey)
				.indexName(PINECONE_INDEX_NAME)
				.namespace(PINECONE_NAMESPACE)
				.contentFieldName(CUSTOM_CONTENT_FIELD_NAME)
				.build();
		}

		@Bean
		public TransformersEmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
