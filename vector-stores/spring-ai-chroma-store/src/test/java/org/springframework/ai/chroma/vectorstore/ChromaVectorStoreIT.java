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

package org.springframework.ai.chroma.vectorstore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.ChromaImage;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Jonghoon Park
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ChromaVectorStoreIT extends BaseVectorStoreTests {

	@Container
	static ChromaDBContainer chromaContainer = new ChromaDBContainer(ChromaImage.DEFAULT_IMAGE);

	private void resetCollection(VectorStore vectorStore) {
		((ChromaVectorStore) vectorStore).deleteCollection();
		((ChromaVectorStore) vectorStore).createCollection();
	}

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

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	public void addAndSearch() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());
			assertThat(results2).hasSize(0);
		});
	}

	@Test
	public void simpleSearch() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			var document = Document.builder()
				.id("simpleDoc")
				.text("The sky is blue because of Rayleigh scattering.")
				.build();

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore.similaritySearch("Why is the sky blue?");

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The sky is blue because of Rayleigh scattering.");

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Why is the sky blue?").build());
			assertThat(results).hasSize(0);
		});
	}

	@Test
	public void addAndSearchWithFilters() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			var request = SearchRequest.builder().query("The World").topK(5).build();

			List<Document> results = vectorStore.similaritySearch(request);
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Bulgaria'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Netherlands'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("NOT(country == 'Netherlands')")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
		});
	}

	@Test
	public void documentUpdateTest() {

		// Note ,using OpenAI to calculate embeddings
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

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

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));
		});
	}

	@Test
	public void searchThresholdTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(this.documents);

			var request = SearchRequest.builder().query("Great").topK(5).build();
			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.from(request).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.from(request).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public RestClient.Builder builder() {
			return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
		}

		@Bean
		public ChromaApi chromaApi(RestClient.Builder builder) {
			return ChromaApi.builder().baseUrl(chromaContainer.getEndpoint()).restClientBuilder(builder).build();
		}

		@Bean
		public VectorStore chromaVectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi) {
			return ChromaVectorStore.builder(chromaApi, embeddingModel)
				.collectionName("TestCollection")
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
