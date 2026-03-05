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

package org.springframework.ai.testcontainers.service.connection.chroma;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Testcontainers
@TestPropertySource(properties = { "spring.ai.vectorstore.chroma.collectionName=TestCollection",
		"spring.ai.vectorstore.chroma.initialize-schema=true" })
class ChromaWithToken2ContainerConnectionDetailsFactoryIT {

	@Container
	@ServiceConnection
	static ChromaDBContainer chroma = new ChromaDBContainer(ChromaImage.DEFAULT_IMAGE)
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS", "token")
		.withEnv("CHROMA_SERVER_AUTH_CREDENTIALS_PROVIDER",
				"chromadb.auth.token.TokenConfigServerAuthCredentialsProvider")
		.withEnv("CHROMA_SERVER_AUTH_PROVIDER", "chromadb.auth.token.TokenAuthServerProvider");

	@Autowired
	private VectorStore vectorStore;

	@Test
	public void addAndSearchWithFilters() {
		var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
				Map.of("country", "Bulgaria"));
		var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
				Map.of("country", "Netherlands"));

		this.vectorStore.add(List.of(bgDocument, nlDocument));

		var request = SearchRequest.builder().query("The World").topK(5).build();

		List<Document> results = this.vectorStore.similaritySearch(request);
		assertThat(results).hasSize(2);

		results = this.vectorStore.similaritySearch(
				SearchRequest.from(request).similarityThresholdAll().filterExpression("country == 'Bulgaria'").build());
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

		results = this.vectorStore.similaritySearch(SearchRequest.from(request)
			.similarityThresholdAll()
			.filterExpression("country == 'Netherlands'")
			.build());
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

		// Remove all documents from the store
		this.vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(ChromaVectorStoreAutoConfiguration.class)
	static class Config {

		@Bean
		public JsonMapper jsonMapper() {
			return new JsonMapper();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
