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

package org.springframework.ai.testcontainers.service.connection.mongo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mongodb.autoconfigure.MongoDBAtlasVectorStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Testcontainers
@TestPropertySource(properties = { "spring.data.mongodb.database=simpleaidb",
		"spring.ai.vectorstore.mongodb.initialize-schema=true",
		"spring.ai.vectorstore.mongodb.collection-name=test_collection",
		"spring.ai.vectorstore.mongodb.index-name=text_index" })
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class MongoDbAtlasLocalContainerConnectionDetailsFactoryIT {

	@Container
	@ServiceConnection
	private static MongoDBAtlasLocalContainer container = new MongoDBAtlasLocalContainer(MongoDbImage.DEFAULT_IMAGE);

	@Autowired
	private VectorStore vectorStore;

	@Test
	public void addAndSearch() throws InterruptedException {
		List<Document> documents = List.of(
				new Document(
						"Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1")),
				new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
				new Document(
						"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
						Collections.singletonMap("meta2", "meta2")));

		this.vectorStore.add(documents);
		Thread.sleep(5000); // Await a second for the document to be indexed

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
		assertThat(resultDoc.getText()).isEqualTo(
				"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
		assertThat(resultDoc.getMetadata()).containsEntry("meta2", "meta2");

		// Remove all documents from the store
		this.vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));

		List<Document> results2 = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());
		assertThat(results2).isEmpty();
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ MongoAutoConfiguration.class, DataMongoAutoConfiguration.class,
			MongoDBAtlasVectorStoreAutoConfiguration.class })
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
