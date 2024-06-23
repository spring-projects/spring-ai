/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import com.couchbase.client.java.Cluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.testcontainer.CouchbaseContainerMetadata.*;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class CouchabaseVectorStoreIT {

	// Define the couchbase container.
	@Container
	final static CouchbaseContainer couchbaseContainer = new CouchbaseContainer(COUCHBASE_IMAGE_ENTERPRISE)
		.withCredentials(USERNAME, PASSWORD)
		.withEnabledServices(CouchbaseService.KV, CouchbaseService.QUERY, CouchbaseService.INDEX,
				CouchbaseService.SEARCH)
		.withBucket(bucketDefinition)
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

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));
			List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));
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

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsEntry("meta1", "meta1");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

			// Remove all documents from the store
			vectorStore.delete(Collections.singletonList(document.getId()));
			List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
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

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(5));
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
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
				.withFilterExpression("NOT(country == 'BG' && year == 2020)"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument.getId(), bgDocument2.getId(), nlDocument.getId()));
			List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			assertThat(results2).isEmpty();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public CouchbaseVectorStore vectorStore(EmbeddingModel embeddingModel) {
			Cluster cluster = Cluster.connect(couchbaseContainer.getConnectionString(),
					couchbaseContainer.getUsername(), couchbaseContainer.getPassword());
			CouchbaseVectorStore.CouchbaseVectorStoreConfig config = CouchbaseVectorStore.CouchbaseVectorStoreConfig
				.builder()
				.withBucketName("springBucket")
				.withScopeName("springScope")
				.withCollectionName("sprtingcollection")
				.build();
			return new CouchbaseVectorStore(cluster, embeddingModel, config, true);
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
