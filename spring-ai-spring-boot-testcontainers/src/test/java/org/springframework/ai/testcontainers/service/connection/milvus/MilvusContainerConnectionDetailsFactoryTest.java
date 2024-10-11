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
package org.springframework.ai.testcontainers.service.connection.milvus;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Testcontainers
@TestPropertySource(properties = { "spring.ai.vectorstore.milvus.metricType=COSINE",
		"spring.ai.vectorstore.milvus.indexType=IVF_FLAT", "spring.ai.vectorstore.milvus.embeddingDimension=384",
		"spring.ai.vectorstore.milvus.collectionName=myTestCollection",
		"spring.ai.vectorstore.milvus.initialize-schema=true" })
class MilvusContainerConnectionDetailsFactoryTest {

	@Container
	@ServiceConnection
	static MilvusContainer milvusContainer = new MilvusContainer(MilvusImage.DEFAULT_IMAGE);

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Autowired
	private VectorStore vectorStore;

	@Test
	public void addAndSearch() {
		vectorStore.add(documents);

		List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
		assertThat(resultDoc.getContent())
			.contains("Spring AI provides abstractions that serve as the foundation for developing AI applications.");
		assertThat(resultDoc.getMetadata()).hasSize(2);
		assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

		// Remove all documents from the store
		vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

		results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
		assertThat(results).hasSize(0);
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(MilvusVectorStoreAutoConfiguration.class)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}