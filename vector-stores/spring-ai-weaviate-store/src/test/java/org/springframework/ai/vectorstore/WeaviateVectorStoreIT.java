/*
 * Copyright 2023-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 */
@Testcontainers
public class WeaviateVectorStoreIT {

	@Container
	static WeaviateContainer weaviateContainer = new WeaviateContainer("semitechnologies/weaviate:1.25.4");

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
		vectorStore.delete(documents.stream().map(Document::getId).toList());
	}

	@Test
	public void addAndSearch() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(documents);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

			results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			assertThat(results).hasSize(0);
		});
	}

	@Test
	public void searchWithFilters() throws InterruptedException {

		contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

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
				.withFilterExpression("NOT((country == 'BG' && year == 2020) || (country == 'NL'))"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			vectorStore.delete(List.of(bgDocument.getId(), nlDocument.getId(), bgDocument2.getId()));
		});
	}

	@Test
	public void documentUpdate() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

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
	public void searchWithThreshold() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThresholdAll());

			List<Double> distances = fullResult.stream()
				.map(doc -> (Double) doc.getMetadata().get("distance"))
				.toList();

			assertThat(distances).hasSize(3);

			double threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(EmbeddingModel embeddingModel) {
			WeaviateClient weaviateClient = new WeaviateClient(
					new Config("http", weaviateContainer.getHttpHostAddress()));

			WeaviateVectorStoreConfig config = WeaviateVectorStore.WeaviateVectorStoreConfig.builder()
				.withFilterableMetadataFields(List.of(MetadataField.text("country"), MetadataField.number("year")))
				.withConsistencyLevel(WeaviateVectorStoreConfig.ConsistentLevel.ONE)
				.build();

			return new WeaviateVectorStore(config, embeddingModel, weaviateClient);

		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}