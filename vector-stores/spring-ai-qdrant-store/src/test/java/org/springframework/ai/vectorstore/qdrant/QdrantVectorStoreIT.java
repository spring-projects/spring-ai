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

package org.springframework.ai.vectorstore.qdrant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Anush Shetty
 * @author Josh Long
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @since 0.8.1
 */
@Testcontainers
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+") })
public class QdrantVectorStoreIT {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int EMBEDDING_DIMENSION = 1024;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer(QdrantImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	@BeforeAll
	static void setup() throws InterruptedException, ExecutionException {

		String host = qdrantContainer.getHost();
		int port = qdrantContainer.getGrpcPort();
		QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());

		client
			.createCollectionAsync(COLLECTION_NAME,
					VectorParams.newBuilder().setDistance(Distance.Cosine).setSize(EMBEDDING_DIMENSION).build())
			.get();

		client.close();
	}

	@Test
	public void addAndSearch() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKeys("meta2", "distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Great").withTopK(1));
			assertThat(results2).hasSize(0);
		});
	}

	@Test
	public void addAndSearchWithFilters() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria", "number", 3));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands", "number", 90));

			vectorStore.add(List.of(bgDocument, nlDocument));

			var request = SearchRequest.query("The World").withTopK(5);

			List<Document> results = vectorStore.similaritySearch(request);
			assertThat(results).hasSize(2);

			results = vectorStore
				.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("country == 'Bulgaria'"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(
					request.withSimilarityThresholdAll().withFilterExpression("country == 'Netherlands'"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(
					request.withSimilarityThresholdAll().withFilterExpression("NOT(country == 'Netherlands')"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore
				.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("number in [3, 5, 12]"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore
				.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("number nin [3, 5, 12]"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
		});
	}

	@Test
	public void documentUpdateTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			vectorStore.delete(List.of(document.getId()));
		});
	}

	@Test
	public void searchThresholdTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			var request = SearchRequest.query("Great").withTopK(5);
			List<Document> fullResult = vectorStore.similaritySearch(request.withSimilarityThresholdAll());

			List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

			assertThat(distances).hasSize(3);

			float threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(request.withSimilarityThreshold(1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public QdrantClient qdrantClient() {
			String host = qdrantContainer.getHost();
			int port = qdrantContainer.getGrpcPort();
			QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
			return qdrantClient;
		}

		@Bean
		public VectorStore qdrantVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
			return new QdrantVectorStore(qdrantClient, COLLECTION_NAME, embeddingModel, true);
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new MistralAiEmbeddingModel(new MistralAiApi(System.getenv("MISTRAL_AI_API_KEY")));
		}

	}

}
