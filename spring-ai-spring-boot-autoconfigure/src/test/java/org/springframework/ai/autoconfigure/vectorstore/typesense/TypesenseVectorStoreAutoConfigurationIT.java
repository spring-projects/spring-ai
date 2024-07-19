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
package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 * @author Eddú Meléndez
 * @author Soby Chacko
 */
@Testcontainers
public class TypesenseVectorStoreAutoConfigurationIT {

	@Container
	private static final GenericContainer<?> typesenseContainer = new GenericContainer<>("typesense/typesense:26.0")
		.withExposedPorts(8108)
		.withCommand("--data-dir", "/tmp", "--api-key=xyz", "--enable-cors")
		.withStartupTimeout(Duration.ofSeconds(100));

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TypesenseVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	public void addAndSearch() {
		contextRunner
			.withPropertyValues("spring.ai.vectorstore.typesense.embeddingDimension=384",
					"spring.ai.vectorstore.typesense.collectionName=myTestCollection",
					"spring.ai.vectorstore.typesense.initialize-schema=true",
					"spring.ai.vectorstore.typesense.client.apiKey=xyz",
					"spring.ai.vectorstore.typesense.client.protocol=http",
					"spring.ai.vectorstore.typesense.client.host=" + typesenseContainer.getHost(),
					"spring.ai.vectorstore.typesense.client.port=" + typesenseContainer.getMappedPort(8108).toString())
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				vectorStore.add(documents);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).hasSize(0);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
