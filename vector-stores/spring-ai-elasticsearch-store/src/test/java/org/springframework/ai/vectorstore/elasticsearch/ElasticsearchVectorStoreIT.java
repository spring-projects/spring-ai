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

package org.springframework.ai.vectorstore.elasticsearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.stats.IndicesStats;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ElasticsearchVectorStoreIT {

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

	private final List<Document> documents = List.of(
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
			// deleting indices and data before following tests
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);
			List indices = elasticsearchClient.cat().indices().valueBody().stream().map(IndicesRecord::index).toList();
			if (!indices.isEmpty()) {
				elasticsearchClient.indices().delete(del -> del.index(indices));
			}
		});
	}

	@Test
	public void addAndDeleteDocumentsTest() {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_cosine",
					ElasticsearchVectorStore.class);
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);

			IndicesStats stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");

			assertThat(stats.total().docs().count()).isEqualTo(0L);

			vectorStore.add(this.documents);
			elasticsearchClient.indices().refresh();
			stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(3L);

			vectorStore.doDelete(List.of("1", "2", "3"));
			elasticsearchClient.indices().refresh();
			stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(0L);
		});
	}

	@Test
	public void deleteDocumentsByFilterExpressionTest() {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_cosine",
					ElasticsearchVectorStore.class);
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);

			IndicesStats stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");

			assertThat(stats.total().docs().count()).isEqualTo(0L);

			// Add documents with metadata
			List<Document> documents = List.of(
					new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
					new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
					new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

			vectorStore.add(documents);
			elasticsearchClient.indices().refresh();

			stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(3L);

			// Delete documents with meta1 using filter expression
			Expression filterExpression = new Expression(ExpressionType.EQ, new Key("meta1"), new Value("meta1"));

			vectorStore.delete(filterExpression);
			elasticsearchClient.indices().refresh();

			stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(2L);

			// Clean up remaining documents
			vectorStore.delete(List.of("2", "3"));
			elasticsearchClient.indices().refresh();

			stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(0L);
		});
	}

	@Test
	public void deleteWithStringFilterExpressionTest() {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_cosine",
					ElasticsearchVectorStore.class);
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);

			List<Document> documents = List.of(
					new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
					new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
					new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

			vectorStore.add(documents);
			elasticsearchClient.indices().refresh();

			// Delete documents with meta1 using string filter
			vectorStore.delete("meta1 == 'meta1'");
			elasticsearchClient.indices().refresh();

			IndicesStats stats = elasticsearchClient.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats.total().docs().count()).isEqualTo(2L);

			// Clean up
			vectorStore.delete(List.of("2", "3"));
			elasticsearchClient.indices().refresh();
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void addAndSearchTest(String similarityFunction) {

		getContextRunner().run(context -> {

			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					ElasticsearchVectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build());

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
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void searchWithFilters(String similarityFunction) {

		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					ElasticsearchVectorStore.class);

			var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020, "activationDate", new Date(1000)));
			var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL", "activationDate", new Date(2000)));
			var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023, "activationDate", new Date(3000)));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build()),
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
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void documentUpdateTest(String similarityFunction) {

		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					ElasticsearchVectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Map.of("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").similarityThresholdAll().topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").similarityThresholdAll().topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner", Map.of("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));
			SearchRequest fooBarSearchRequest = SearchRequest.builder()
				.query("FooBar")
				.topK(5)
				.similarityThresholdAll()
				.build();

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
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void searchThresholdTest(String similarityFunction) {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction,
					ElasticsearchVectorStore.class);

			vectorStore.add(this.documents);

			SearchRequest query = SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
				.similarityThresholdAll()
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
						SearchRequest.builder().query("Great Depression").topK(50).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@Test
	public void overDefaultSizeTest() {

		var overDefaultSize = 12;

		getContextRunner().run(context -> {

			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_cosine",
					ElasticsearchVectorStore.class);

			var testDocs = new ArrayList<Document>();
			for (int i = 0; i < overDefaultSize; i++) {
				testDocs.add(new Document(String.valueOf(i), "Great Depression " + i, Map.of()));
			}
			vectorStore.add(testDocs);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Great Depression")
				.topK(overDefaultSize)
				.similarityThresholdAll()
				.build());

			assertThat(results).hasSize(overDefaultSize);

			// Remove all documents from the store
			vectorStore.delete(testDocs.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@Test
	public void getNativeClientTest() {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_cosine",
					ElasticsearchVectorStore.class);

			// Test successful native client retrieval
			Optional<ElasticsearchClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();

			// Verify client functionality
			ElasticsearchClient client = nativeClient.get();
			IndicesStats stats = client.indices()
				.stats(s -> s.index("spring-ai-document-index"))
				.indices()
				.get("spring-ai-document-index");
			assertThat(stats).isNotNull();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean("vectorStore_cosine")
		public ElasticsearchVectorStore vectorStoreDefault(EmbeddingModel embeddingModel, RestClient restClient) {
			return ElasticsearchVectorStore.builder(restClient, embeddingModel).initializeSchema(true).build();
		}

		@Bean("vectorStore_l2_norm")
		public ElasticsearchVectorStore vectorStoreL2(EmbeddingModel embeddingModel, RestClient restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("index_l2");
			options.setSimilarity(SimilarityFunction.l2_norm);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean("vectorStore_dot_product")
		public ElasticsearchVectorStore vectorStoreDotProduct(EmbeddingModel embeddingModel, RestClient restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("index_dot_product");
			options.setSimilarity(SimilarityFunction.dot_product);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
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
