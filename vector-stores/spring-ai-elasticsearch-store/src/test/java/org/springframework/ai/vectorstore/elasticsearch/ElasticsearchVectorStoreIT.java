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
import java.net.URISyntaxException;
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
import java.util.function.Consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.stats.IndicesStats;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.awaitility.Awaitility;
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
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ElasticsearchVectorStoreIT extends BaseVectorStoreTests {

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
			List indices = elasticsearchClient.cat().indices().indices().stream().map(IndicesRecord::index).toList();
			if (!indices.isEmpty()) {
				elasticsearchClient.indices().delete(del -> del.index(indices));
			}
		});
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean("vectorStore_cosine", VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "custom_embedding_field" })
	public void addAndDeleteDocumentsTest(String vectorStoreBeanName) {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + vectorStoreBeanName,
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

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product", "custom_embedding_field" })
	public void addAndSearchTest(String vectorStoreBeanName) {

		getContextRunner().run(context -> {

			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + vectorStoreBeanName,
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
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product", "custom_embedding_field" })
	public void searchWithFilters(String vectorStoreBeanName) {

		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + vectorStoreBeanName,
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
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product", "custom_embedding_field" })
	public void documentUpdateTest(String vectorStoreBeanName) {

		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + vectorStoreBeanName,
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
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product", "custom_embedding_field" })
	public void searchThresholdTest(String vectorStoreBeanName) {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean("vectorStore_" + vectorStoreBeanName,
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
	public static class TestApplication {

		@Bean("vectorStore_cosine")
		public ElasticsearchVectorStore vectorStoreDefault(EmbeddingModel embeddingModel, Rest5Client restClient) {
			return ElasticsearchVectorStore.builder(restClient, embeddingModel).initializeSchema(true).build();
		}

		@Bean("vectorStore_l2_norm")
		public ElasticsearchVectorStore vectorStoreL2(EmbeddingModel embeddingModel, Rest5Client restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("index_l2");
			options.setSimilarity(SimilarityFunction.l2_norm);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean("vectorStore_dot_product")
		public ElasticsearchVectorStore vectorStoreDotProduct(EmbeddingModel embeddingModel, Rest5Client restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("index_dot_product");
			options.setSimilarity(SimilarityFunction.dot_product);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean("vectorStore_custom_embedding_field")
		public ElasticsearchVectorStore vectorStoreCustomField(EmbeddingModel embeddingModel, Rest5Client restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setEmbeddingFieldName("custom_embedding_field");
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

		@Bean
		Rest5Client restClient() throws URISyntaxException {
			return Rest5Client.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();
		}

		@Bean
		ElasticsearchClient elasticsearchClient(Rest5Client restClient) {
			return new ElasticsearchClient(new Rest5ClientTransport(restClient, new JacksonJsonpMapper(
					new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))));
		}

	}

}
