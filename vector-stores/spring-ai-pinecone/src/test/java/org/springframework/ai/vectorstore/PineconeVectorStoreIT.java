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

package org.springframework.ai.vectorstore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.PineconeVectorStore.PineconeVectorStoreConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PineconeVectorStoreIT {

	// Replace the PINECONE_ENVIRONMENT, PINECONE_PROJECT_ID, PINECONE_INDEX_NAME and
	// PINECONE_API_KEY with your pinecone credentials.
	private static final String PINECONE_ENVIRONMENT = "gcp-starter";

	private static final String PINECONE_PROJECT_ID = "814621f";

	private static final String PINECONE_INDEX_NAME = "spring-ai-test-index";

	// NOTE: Leave it empty as for free tier as later doesn't support namespaces.
	private static final String PINECONE_NAMESPACE = "";

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ONE_MINUTE);
	}

	@Test
	public void addAndSearchTest() {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("Great", 1);
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch("Great", 1);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("Hello", 1);
			}, hasSize(0));
		});
	}

	@Test
	public void addAndSearchWithFilters() {

		// Pinecone metadata filtering syntax:
		// https://docs.pinecone.io/docs/metadata-filtering

		final double THRESHOLD_ALL = 0.0;

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherland"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("The World", 1);
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch("The World", 5);
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch("The World", 5, THRESHOLD_ALL, "country == 'Bulgaria'");
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch("The World", 5, THRESHOLD_ALL, "country == 'Netherland'");
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("The World", 1);
			}, hasSize(0));
		});
	}

	@Test
	public void documentUpdateTest() {

		// Note ,using OpenAI to calculate embeddings
		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("Spring", 5);
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch("Spring", 5);

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

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("FooBar", 5).get(0).getContent();
			}, equalTo("The World is Big and Salvation Lurks Around the Corner"));

			results = vectorStore.similaritySearch("FooBar", 5);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));
			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("FooBar", 1);
			}, hasSize(0));

		});
	}

	@Test
	public void searchThresholdTest() {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("Great", 5);
			}, hasSize(3));

			List<Document> fullResult = vectorStore.similaritySearch("Great", 5, 0.0);

			List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

			assertThat(distances).hasSize(3);

			float threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch("Great", 5, (1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());
			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch("Hello", 1);
			}, hasSize(0));
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public PineconeVectorStoreConfig pineconeVectorStoreConfig() {

			return PineconeVectorStoreConfig.builder()
				.withApiKey(System.getenv("PINECONE_API_KEY"))
				.withEnvironment(PINECONE_ENVIRONMENT)
				.withProjectId(PINECONE_PROJECT_ID)
				.withIndexName(PINECONE_INDEX_NAME)
				.withNamespace(PINECONE_NAMESPACE)
				.build();
		}

		@Bean
		public VectorStore vectorStore(PineconeVectorStoreConfig config, EmbeddingClient embeddingClient) {
			return new PineconeVectorStore(config, embeddingClient);
		}

	}

}