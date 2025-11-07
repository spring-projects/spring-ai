/*
 * Copyright 2025-2025 the original author or authors.
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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.couchbase.client.java.Cluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.testcontainer.CouchbaseContainerMetadata;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class CouchbaseSearchVectorStoreIT {

	// Define the couchbase container.
	@Container
	final static CouchbaseContainer couchbaseContainer = new CouchbaseContainer(
			CouchbaseContainerMetadata.COUCHBASE_IMAGE_ENTERPRISE)
		.withCredentials(CouchbaseContainerMetadata.USERNAME, CouchbaseContainerMetadata.PASSWORD)
		.withEnabledServices(CouchbaseService.KV, CouchbaseService.QUERY, CouchbaseService.INDEX,
				CouchbaseService.SEARCH)
		.withBucket(CouchbaseContainerMetadata.bucketDefinition)
		.withStartupAttempts(4)
		.withStartupTimeout(Duration.ofSeconds(90))
		.waitingFor(Wait.forHealthcheck());

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
	}

	@AfterAll
	public static void stopContainers() {
		couchbaseContainer.close();
	}

	@Test
	void vectorStoreTest() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			List<Document> documents = List.of(
					new Document(
							"Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
							Collections.singletonMap("meta1", "meta1")),
					new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
					new Document(
							"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
							Collections.singletonMap("meta2", "meta2")));
			vectorStore.add(documents);
			Thread.sleep(5000); // wait for indexing

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));
			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());
			assertThat(results2).isEmpty();

		});
	}

	@Test
	void documentUpdateTest() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));
			Thread.sleep(5000); // Await a second for the document to be indexed

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsEntry("meta1", "meta1");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

			// Remove all documents from the store
			vectorStore.delete(Collections.singletonList(document.getId()));
			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results2).isEmpty();
		});
	}

	@Test
	void searchWithFilters() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));
			Thread.sleep(5000); // Await a second for the document to be indexed

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
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
				.filterExpression("NOT(country == 'BG' && year == 2020)")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument.getId(), bgDocument2.getId(), nlDocument.getId()));
			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results2).isEmpty();

		});
	}

	@Test
	void deleteWithComplexFilterExpression() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var doc1 = new Document("Content 1", Map.of("type", "A", "priority", 1));
			var doc2 = new Document("Content 2", Map.of("type", "A", "priority", 2));
			var doc3 = new Document("Content 3", Map.of("type", "B", "priority", 1));

			vectorStore.add(List.of(doc1, doc2, doc3));
			Thread.sleep(5000); // Wait for indexing

			// Complex filter expression: (type == 'A' AND priority > 1)
			Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
					new Filter.Key("priority"), new Filter.Value(1));
			Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
					new Filter.Value("A"));
			Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
					priorityFilter);

			vectorStore.delete(complexFilter);
			Thread.sleep(1000); // Wait for deletion to be processed

			var results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("A", "B");
			assertThat(results.stream().map(doc -> doc.getMetadata().get("priority")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(1, 1);

			// Remove all documents from the store
			vectorStore.delete(List.of(doc1.getId(), doc3.getId()));
			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).build());
			assertThat(results2).isEmpty();
		});
	}

	@Test
	void getNativeClientTest() {
		getContextRunner().run(context -> {
			CouchbaseSearchVectorStore vectorStore = context.getBean(CouchbaseSearchVectorStore.class);
			Optional<CouchbaseSearchVectorStore> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public CouchbaseSearchVectorStore vectorStore(EmbeddingModel embeddingModel) {
			Cluster cluster = Cluster.connect(couchbaseContainer.getConnectionString(),
					couchbaseContainer.getUsername(), couchbaseContainer.getPassword());
			CouchbaseSearchVectorStore.Builder builder = CouchbaseSearchVectorStore.builder(cluster, embeddingModel)
				.bucketName("springBucket")
				.scopeName("springScope")
				.collectionName("springCollection");

			return builder.initializeSchema(true).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
