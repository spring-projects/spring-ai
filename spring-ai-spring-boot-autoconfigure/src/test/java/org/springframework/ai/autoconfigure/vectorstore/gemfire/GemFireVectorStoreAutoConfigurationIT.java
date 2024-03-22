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

package org.springframework.ai.autoconfigure.vectorstore.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.GemFireVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author Geet Rawat
 */

class GemFireVectorStoreAutoConfigurationIT {

	public static final String INDEX_NAME = "spring-ai-index";

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GemFireVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.gemfire.indexName=spring-ai-index");

	@BeforeEach
	public void createIndex() {
		contextRunner.run(c -> c.getBean(GemFireVectorStore.class).createIndex(INDEX_NAME));
	}

	@AfterEach
	public void deleteIndex() {
		contextRunner.run(c -> c.getBean(GemFireVectorStore.class).deleteIndex(INDEX_NAME));
	}

	@Test
	public void addAndSearchTest() {

		contextRunner.withPropertyValues("spring.ai.vectorstore.gemfire.indexName=" + INDEX_NAME).run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(documents);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			}, hasSize(0));
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingClient embeddingClient() {
			return new TransformersEmbeddingClient();
		}

	}

}
