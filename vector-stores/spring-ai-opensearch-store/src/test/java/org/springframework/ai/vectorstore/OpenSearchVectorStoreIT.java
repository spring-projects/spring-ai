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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
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
	private static final OpensearchContainer<?> opensearchContainer = new OpensearchContainer<>(
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
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
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
				.until(() -> vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(5)), hasSize(3));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'NL'"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'BG'"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'BG' && year == 2020"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country in ['BG']"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country in ['BG','NL']"));

			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country not in ['BG']"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("NOT(country not in ['BG'])"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression(
						"activationDate > " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli()));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(1)), hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void documentUpdateTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);
			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Map.of("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Spring").withSimilarityThreshold(0).withTopK(5)),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Spring").withSimilarityThreshold(0).withTopK(5));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner", Map.of("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));
			SearchRequest fooBarSearchRequest = SearchRequest.query("FooBar").withTopK(5);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(fooBarSearchRequest).get(0).getContent(),
						equalTo("The World is Big and Salvation Lurks Around the Corner"));

			results = vectorStore.similaritySearch(fooBarSearchRequest);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));

			Awaitility.await().until(() -> vectorStore.similaritySearch(fooBarSearchRequest), hasSize(0));

		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void searchThresholdTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);
			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

			vectorStore.add(this.documents);

			SearchRequest query = SearchRequest.query("Great Depression")
				.withTopK(50)
				.withSimilarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);

			Awaitility.await().until(() -> vectorStore.similaritySearch(query), hasSize(3));

			List<Document> fullResult = vectorStore.similaritySearch(query);

			List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

			assertThat(distances).hasSize(3);

			float threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.query("Great Depression").withTopK(50).withSimilarityThreshold(1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(50).withSimilarityThreshold(0)),
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

			List<Document> resultInIndex1 = vectorStore1
				.similaritySearch(SearchRequest.query("Document in index 1").withTopK(1).withSimilarityThreshold(0));

			List<Document> resultInIndex2 = vectorStore2
				.similaritySearch(SearchRequest.query("Document in index 2").withTopK(1).withSimilarityThreshold(0));

			// then
			assertThat(resultInIndex1).hasSize(1);
			assertThat(resultInIndex1.get(0).getId()).isEqualTo(docInIndex1.getId());

			assertThat(resultInIndex2).hasSize(1);
			assertThat(resultInIndex2.get(0).getId()).isEqualTo(docInIndex2.getId());
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		@Qualifier("vectorStore")
		public OpenSearchVectorStore vectorStore(EmbeddingModel embeddingModel) {
			try {
				return new OpenSearchVectorStore(new OpenSearchClient(ApacheHttpClient5TransportBuilder
					.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
					.build()), embeddingModel, true);
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		@Qualifier("anotherVectorStore")
		public OpenSearchVectorStore anotherVectorStore(EmbeddingModel embeddingModel) {
			try {
				return new OpenSearchVectorStore("another_index",
						new OpenSearchClient(ApacheHttpClient5TransportBuilder
							.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
							.build()),
						embeddingModel, OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION,
						true);
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
