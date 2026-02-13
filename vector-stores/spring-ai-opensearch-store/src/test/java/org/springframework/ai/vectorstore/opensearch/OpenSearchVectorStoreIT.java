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

package org.springframework.ai.vectorstore.opensearch;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.HttpHost;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * The OpenSearchVectorStoreIT class is a test class designed to validate the
 * functionality of a vector store that integrates with OpenSearch. It contains multiple
 * parameterized tests to ensure the correctness of storing, searching, and updating
 * vectorized documents in OpenSearch.
 *
 * @author Jemin Huh
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author inpink
 * @since 1.0.0
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenSearchVectorStoreIT {

	@Container
	private static final OpenSearchContainer<?> opensearchContainer = new OpenSearchContainer<>(
			OpenSearchImage.DEFAULT_IMAGE);

	private static final String DEFAULT = "cosinesimil";

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	private String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
	}

	@BeforeEach
	void cleanDatabase() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);
			vectorStore.delete(List.of("_all"));

			VectorStore anotherVectorStore = context.getBean("anotherVectorStore", OpenSearchVectorStore.class);
			anotherVectorStore.delete(List.of("_all"));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void addAndSearchTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					OpenSearchVectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void searchWithFilters(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

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

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(
						"activationDate > " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli())
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(1).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void documentUpdateTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					OpenSearchVectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Map.of("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").similarityThreshold(0).topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").similarityThreshold(0).topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner", Map.of("meta2", "meta2"));

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

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void searchThresholdTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					OpenSearchVectorStore.class);

			vectorStore.add(this.documents);

			SearchRequest query = SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
				.similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
				.build();

			Awaitility.await().until(() -> vectorStore.similaritySearch(query), hasSize(3));

			List<Document> fullResult = vectorStore.similaritySearch(query);

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
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
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(50).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@Test
	@Disabled("GH-1645")
	public void searchDocumentsInTwoIndicesTest() {
		getContextRunner().run(context -> {
			// given
			OpenSearchVectorStore vectorStore1 = context.getBean("vectorStore", OpenSearchVectorStore.class);
			OpenSearchVectorStore vectorStore2 = context.getBean("anotherVectorStore", OpenSearchVectorStore.class);

			Document docInIndex1 = new Document("1", "Document in index 1", Map.of("meta", "index1"));
			Document docInIndex2 = new Document("2", "Document in index 2", Map.of("meta", "index2"));

			// when
			vectorStore1.add(List.of(docInIndex1));
			vectorStore2.add(List.of(docInIndex2));

			List<Document> resultInIndex1 = vectorStore1.similaritySearch(
					SearchRequest.builder().query("Document in index 1").topK(1).similarityThreshold(0).build());

			List<Document> resultInIndex2 = vectorStore2.similaritySearch(
					SearchRequest.builder().query("Document in index 2").topK(1).similarityThreshold(0).build());

			// then
			assertThat(resultInIndex1).hasSize(1);
			assertThat(resultInIndex1.get(0).getId()).isEqualTo(docInIndex1.getId());

			assertThat(resultInIndex2).hasSize(1);
			assertThat(resultInIndex2.get(0).getId()).isEqualTo(docInIndex2.getId());
		});
	}

	@Test
	void deleteById() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

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

			vectorStore.delete(List.of(bgDocument.getId(), bgDocument2.getId()));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");
		});
	}

	@Test
	void deleteByFilter() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

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

			Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.EQ,
					new Filter.Key("country"), new Filter.Value("BG"));

			vectorStore.delete(filterExpression);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");
		});
	}

	@Test
	void deleteWithStringFilterExpression() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

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

			vectorStore.delete("country == 'BG'");

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");
		});
	}

	@Test
	void deleteWithComplexFilterExpression() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			var doc1 = new Document("1", "Content 1", Map.of("type", "A", "priority", 1));
			var doc2 = new Document("2", "Content 2", Map.of("type", "A", "priority", 2));
			var doc3 = new Document("3", "Content 3", Map.of("type", "B", "priority", 1));

			vectorStore.add(List.of(doc1, doc2, doc3));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Content").topK(5).build()),
						hasSize(3));

			// Complex filter expression: (type == 'A' AND priority > 1)
			Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
					new Filter.Key("priority"), new Filter.Value(1));
			Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
					new Filter.Value("A"));
			Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
					priorityFilter);

			vectorStore.delete(complexFilter);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Content").topK(5).build()),
						hasSize(2));

			var results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("A", "B");
			assertThat(results.stream().map(doc -> doc.getMetadata().get("priority")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(1, 1);
		});
	}

	@Test
	void getNativeClientTest() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);
			Optional<OpenSearchClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l2", "innerproduct" })
	public void approximateAddAndSearchTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_approximate_" + similarityFunction,
					OpenSearchVectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0.0).build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0.0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0.0).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l2", "innerproduct" })
	public void approximateSearchWithFilters(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_approximate_" + similarityFunction,
					OpenSearchVectorStore.class);

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

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(
						"activationDate > " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli())
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId(), bgDocument2.getId()));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(1).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l2", "innerproduct" })
	public void approximateDocumentUpdateTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_approximate_" + similarityFunction,
					OpenSearchVectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Map.of("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").similarityThreshold(0.0).topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").similarityThreshold(0.0).topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner", Map.of("meta2", "meta2"));

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

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l2" })
	public void approximateSearchThresholdTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore_approximate_" + similarityFunction,
					OpenSearchVectorStore.class);

			vectorStore.add(this.documents);

			SearchRequest query = SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
				.similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
				.build();

			Awaitility.await().until(() -> vectorStore.similaritySearch(query), hasSize(3));

			List<Document> fullResult = vectorStore.similaritySearch(query);

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
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
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(50).similarityThreshold(0.0).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "manageDocumentIds={0}")
	@ValueSource(booleans = { true, false })
	void testManageDocumentIdsSetting(boolean manageDocumentIds) {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			// Create a new vector store with specific manageDocumentIds setting
			OpenSearchVectorStore testVectorStore = OpenSearchVectorStore
				.builder((OpenSearchClient) vectorStore.getNativeClient().orElseThrow(),
						context.getBean(EmbeddingModel.class))
				.manageDocumentIds(manageDocumentIds)
				.index("test_manage_document_ids_" + manageDocumentIds)
				.initializeSchema(true)
				.build();

			// Ensure the index is fully initialized before adding documents
			testVectorStore.afterPropertiesSet();

			// Test documents
			List<Document> testDocuments = List.of(new Document("doc1", "Test content 1", Map.of("key1", "value1")),
					new Document("doc2", "Test content 2", Map.of("key2", "value2")));

			// Add documents
			testVectorStore.add(testDocuments);

			// Wait for indexing
			Awaitility.await()
				.until(() -> testVectorStore
					.similaritySearch(SearchRequest.builder().query("Test content").topK(2).build()), hasSize(2));

			// Search and verify results
			List<Document> results = testVectorStore
				.similaritySearch(SearchRequest.builder().query("Test content").topK(2).build());

			assertThat(results).hasSize(2);

			// Verify document content and metadata are preserved
			assertThat(results.stream().map(Document::getText).toList()).containsExactlyInAnyOrder("Test content 1",
					"Test content 2");

			assertThat(results.stream().map(doc -> doc.getMetadata().get("key1")).toList()).contains("value1");
			assertThat(results.stream().map(doc -> doc.getMetadata().get("key2")).toList()).contains("value2");

			// Clean up
			testVectorStore.delete(testDocuments.stream().map(Document::getId).toList());
		});
	}

	@Test
	void testManageDocumentIdsFalseForAWSOpenSearchServerless() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			// Create vector store with manageDocumentIds=false (AWS OpenSearch Serverless
			// mode)
			OpenSearchVectorStore awsCompatibleVectorStore = OpenSearchVectorStore
				.builder((OpenSearchClient) vectorStore.getNativeClient().orElseThrow(),
						context.getBean(EmbeddingModel.class))
				.manageDocumentIds(false)
				.index("test_aws_serverless_compatible")
				.initializeSchema(true)
				.build();

			// Ensure the index is fully initialized before adding documents
			awsCompatibleVectorStore.afterPropertiesSet();

			// Test documents with IDs (these should be ignored when
			// manageDocumentIds=false)
			List<Document> testDocuments = List.of(
					new Document("custom-id-1", "AWS Serverless content 1", Map.of("env", "aws-serverless")),
					new Document("custom-id-2", "AWS Serverless content 2", Map.of("env", "aws-serverless")));

			// Add documents - should work without explicit document ID errors
			awsCompatibleVectorStore.add(testDocuments);

			// Wait for indexing
			Awaitility.await()
				.until(() -> awsCompatibleVectorStore
					.similaritySearch(SearchRequest.builder().query("AWS Serverless").topK(2).build()), hasSize(2));

			// Search and verify results
			List<Document> results = awsCompatibleVectorStore
				.similaritySearch(SearchRequest.builder().query("AWS Serverless").topK(2).build());

			assertThat(results).hasSize(2);

			// Verify content is preserved
			assertThat(results.stream().map(Document::getText).toList())
				.containsExactlyInAnyOrder("AWS Serverless content 1", "AWS Serverless content 2");

			// Verify metadata is preserved
			assertThat(results.stream().map(doc -> doc.getMetadata().get("env")).toList())
				.containsOnly("aws-serverless");

			// Clean up
			awsCompatibleVectorStore.delete(List.of("_all"));
		});
	}

	@Test
	void testManageDocumentIdsTrueWithExplicitIds() {
		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			// Create vector store with manageDocumentIds=true (default behavior)
			OpenSearchVectorStore explicitIdVectorStore = OpenSearchVectorStore
				.builder((OpenSearchClient) vectorStore.getNativeClient().orElseThrow(),
						context.getBean(EmbeddingModel.class))
				.manageDocumentIds(true)
				.index("test_explicit_ids")
				.initializeSchema(true)
				.build();

			// Ensure the index is fully initialized before adding documents
			explicitIdVectorStore.afterPropertiesSet();

			// Test documents with specific IDs
			List<Document> testDocuments = List.of(
					new Document("explicit-id-1", "Explicit ID content 1", Map.of("type", "explicit")),
					new Document("explicit-id-2", "Explicit ID content 2", Map.of("type", "explicit")));

			// Add documents
			explicitIdVectorStore.add(testDocuments);

			// Wait for indexing
			Awaitility.await()
				.until(() -> explicitIdVectorStore
					.similaritySearch(SearchRequest.builder().query("Explicit ID").topK(2).build()), hasSize(2));

			// Search and verify results
			List<Document> results = explicitIdVectorStore
				.similaritySearch(SearchRequest.builder().query("Explicit ID").topK(2).build());

			assertThat(results).hasSize(2);

			// Verify document IDs are preserved
			assertThat(results.stream().map(Document::getId).toList()).containsExactlyInAnyOrder("explicit-id-1",
					"explicit-id-2");

			// Verify content and metadata
			assertThat(results.stream().map(Document::getText).toList())
				.containsExactlyInAnyOrder("Explicit ID content 1", "Explicit ID content 2");

			assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).toList()).containsOnly("explicit");

			// Test deletion by specific IDs
			explicitIdVectorStore.delete(List.of("explicit-id-1"));

			Awaitility.await()
				.until(() -> explicitIdVectorStore
					.similaritySearch(SearchRequest.builder().query("Explicit ID").topK(2).build()), hasSize(1));

			// Verify only one document remains
			results = explicitIdVectorStore
				.similaritySearch(SearchRequest.builder().query("Explicit ID").topK(2).build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo("explicit-id-2");

			// Clean up
			explicitIdVectorStore.delete(List.of("explicit-id-2"));
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public OpenSearchClient openSearchClient() {
			try {
				return new OpenSearchClient(ApacheHttpClient5TransportBuilder
					.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
					.build());
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		@Qualifier("vectorStore")
		public OpenSearchVectorStore vectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel).initializeSchema(true).build();
		}

		@Bean
		@Qualifier("anotherVectorStore")
		public OpenSearchVectorStore anotherVectorStore(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("another_index")
				.mappingJson(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION)
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_" + DEFAULT)
		public OpenSearchVectorStore vectorStoreDefault(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_cosinesimil")
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_l2")
		public OpenSearchVectorStore vectorStoreL2(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_l2")
				.similarityFunction("l2")
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_innerproduct")
		public OpenSearchVectorStore vectorStoreInnerproduct(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_innerproduct")
				.similarityFunction("innerproduct")
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_l1")
		public OpenSearchVectorStore vectorStoreL1(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_l1")
				.similarityFunction("l1")
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_linf")
		public OpenSearchVectorStore vectorStoreLinf(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_linf")
				.similarityFunction("linf")
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_approximate_" + DEFAULT)
		public OpenSearchVectorStore vectorStoreApproximateDefault(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_approximate_cosinesimil")
				.useApproximateKnn(true)
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_approximate_l2")
		public OpenSearchVectorStore vectorStoreApproximateL2(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_approximate_l2")
				.similarityFunction("l2")
				.useApproximateKnn(true)
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_approximate_innerproduct")
		public OpenSearchVectorStore vectorStoreApproximateInnerproduct(OpenSearchClient openSearchClient,
				EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
				.index("index_approximate_innerproduct")
				.similarityFunction("innerproduct")
				.useApproximateKnn(true)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
