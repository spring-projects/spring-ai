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

package org.springframework.ai.vectorstore.weaviate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
public class WeaviateVectorStoreIT extends BaseVectorStoreTests {

	@Container
	static WeaviateContainer weaviateContainer = new WeaviateContainer(WeaviateImage.DEFAULT_IMAGE)
		.waitingFor(Wait.forHttp("/v1/.well-known/ready").forPort(8080));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("471a8c78-549a-4b2c-bce5-ef3ae6579be3", getText("classpath:/test/data/spring.ai.txt"),
					Map.of("meta1", "meta1")),
			new Document("bc51d7f7-627b-4ba6-adf4-f0bcd1998f8f", getText("classpath:/test/data/time.shelter.txt"),
					Map.of()),
			new Document("d0237682-1150-44ff-b4d2-1be9b1731ee5", getText("classpath:/test/data/great.depression.txt"),
					Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void resetCollection(VectorStore vectorStore) {
		initCollection(vectorStore);
		vectorStore.delete(this.documents.stream().map(Document::getId).toList());
	}

	// This method is used to resolve errors that occur when it is executed independently
	// without BaseVectorStoreTests.
	private void initCollection(VectorStore vectorStore) {
		List<Document> dummyDocuments = List.of(new Document("", Map.of("country", "", "year", 0)));
		vectorStore.add(dummyDocuments);
		vectorStore.delete(List.of(dummyDocuments.get(0).getId()));
	}

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
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results).hasSize(0);
		});
	}

	@Test
	public void searchWithFilters() throws InterruptedException {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			resetCollection(vectorStore);

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

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
				.filterExpression("NOT((country == 'BG' && year == 2020) || (country == 'NL'))")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId(), bgDocument2.getId()));
		});
	}

	@Test
	public void documentUpdate() {

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

			vectorStore.delete(List.of(document.getId()));

		});
	}

	@Test
	public void searchWithThreshold() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(this.documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Spring").topK(5).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			WeaviateVectorStore vectorStore = context.getBean(WeaviateVectorStore.class);
			Optional<WeaviateClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	public void addAndSearchWithCustomObjectClass() {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			resetCollection(vectorStore);
		});

		this.contextRunner.run(context -> {
			WeaviateClient weaviateClient = context.getBean(WeaviateClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			WeaviateVectorStoreOptions optionsWithCustomObjectClass = new WeaviateVectorStoreOptions();
			optionsWithCustomObjectClass.setObjectClass("CustomObjectClass");

			VectorStore customVectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
				.options(optionsWithCustomObjectClass)
				.build();

			resetCollection(customVectorStore);
			customVectorStore.add(this.documents);

			List<Document> results = customVectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertFalse(results.isEmpty());
		});

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertTrue(results.isEmpty());
		});
	}

	@Test
	public void addAndSearchWithCustomContentFieldName() {

		WeaviateVectorStoreOptions optionsWithCustomContentFieldName = new WeaviateVectorStoreOptions();
		optionsWithCustomContentFieldName.setContentFieldName("customContentFieldName");

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			resetCollection(vectorStore);
		});

		this.contextRunner.run(context -> {
			WeaviateClient weaviateClient = context.getBean(WeaviateClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			VectorStore customVectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
				.options(optionsWithCustomContentFieldName)
				.build();

			customVectorStore.add(this.documents);

			List<Document> results = customVectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertFalse(results.isEmpty());
		});

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			assertThatThrownBy(
					() -> vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("exactly one of text or media must be specified");
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "custom_", "" })
	public void addAndSearchWithCustomMetaFieldPrefix(String metaFieldPrefix) {
		WeaviateVectorStoreOptions optionsWithCustomContentFieldName = new WeaviateVectorStoreOptions();
		optionsWithCustomContentFieldName.setMetaFieldPrefix(metaFieldPrefix);

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			resetCollection(vectorStore);
		});

		this.contextRunner.run(context -> {
			WeaviateClient weaviateClient = context.getBean(WeaviateClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			VectorStore customVectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
				.filterMetadataFields(List.of(WeaviateVectorStore.MetadataField.text("country")))
				.options(optionsWithCustomContentFieldName)
				.build();

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			customVectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			List<Document> results = customVectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
			assertThat(results).hasSize(3);

			results = customVectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());
		});

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());
			assertThat(results).hasSize(0);
		});

		// remove documents for parameterized test
		this.contextRunner.run(context -> {
			WeaviateClient weaviateClient = context.getBean(WeaviateClient.class);
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			VectorStore customVectorStore = WeaviateVectorStore.builder(weaviateClient, embeddingModel)
				.filterMetadataFields(List.of(WeaviateVectorStore.MetadataField.text("country")))
				.options(optionsWithCustomContentFieldName)
				.build();

			List<Document> results = customVectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());

			customVectorStore.delete(results.stream().map(Document::getId).toList());
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
			return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
				.filterMetadataFields(List.of(WeaviateVectorStore.MetadataField.text("country"),
						WeaviateVectorStore.MetadataField.number("year")))
				.consistencyLevel(WeaviateVectorStore.ConsistentLevel.ONE)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		public WeaviateClient weaviateClient() {
			return new WeaviateClient(new Config("http", weaviateContainer.getHttpHostAddress()));
		}

	}

}
