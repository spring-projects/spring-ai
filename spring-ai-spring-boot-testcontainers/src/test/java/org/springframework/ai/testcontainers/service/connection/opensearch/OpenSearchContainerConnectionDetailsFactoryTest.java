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

package org.springframework.ai.testcontainers.service.connection.opensearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.autoconfigure.vectorstore.opensearch.OpenSearchVectorStoreAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.OpenSearchVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(properties = {
		"spring.ai.vectorstore.opensearch.index-name=" + OpenSearchContainerConnectionDetailsFactoryTest.DOCUMENT_INDEX,
		"spring.ai.vectorstore.opensearch.initialize-schema=true",
		"spring.ai.vectorstore.opensearch.mapping-json="
				+ OpenSearchContainerConnectionDetailsFactoryTest.MAPPING_JSON })
@Testcontainers
class OpenSearchContainerConnectionDetailsFactoryTest {

	static final String DOCUMENT_INDEX = "auto-spring-ai-document-index";

	static final String MAPPING_JSON = "{\"properties\":{\"embedding\":{\"type\":\"knn_vector\",\"dimension\":384}}}";

	@Container
	@ServiceConnection
	private static final OpensearchContainer<?> opensearch = new OpensearchContainer<>(OpenSearchImage.DEFAULT_IMAGE);

	private final List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@Autowired
	private OpenSearchVectorStore vectorStore;

	@Test
	public void addAndSearchTest() {

		this.vectorStore.add(this.documents);

		Awaitility.await()
			.until(() -> this.vectorStore
				.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
					hasSize(1));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0));

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
		assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
		assertThat(resultDoc.getMetadata()).hasSize(2);
		assertThat(resultDoc.getMetadata()).containsKey("meta2");
		assertThat(resultDoc.getMetadata()).containsKey("distance");

		// Remove all documents from the store
		this.vectorStore.delete(this.documents.stream().map(Document::getId).toList());

		Awaitility.await()
			.until(() -> this.vectorStore
				.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
					hasSize(0));
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

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(OpenSearchVectorStoreAutoConfiguration.class)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
