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

package org.springframework.ai.autoconfigure.vectorstore.neo4j;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.autoconfigure.vectorstore.observation.ObservationTestUtil.assertObservationRegistry;

/**
 * @author Jingzhou Ou
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
public class Neo4jVectorStoreAutoConfigurationIT {

	// Needs to be Neo4j 5.15+, because Neo4j 5.15 deprecated the used embedding storing
	// function.
	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.18"))
		.withRandomPassword();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Neo4jAutoConfiguration.class, Neo4jVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.neo4j.uri=" + neo4jContainer.getBoltUrl(),
				"spring.ai.vectorstore.neo4j.initialize-schema=true", "spring.neo4j.authentication.username=" + "neo4j",
				"spring.neo4j.authentication.password=" + neo4jContainer.getAdminPassword());

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	void addAndSearch() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.neo4j.label=my_test_label",
					"spring.ai.vectorstore.neo4j.embeddingDimension=384",
					"spring.ai.vectorstore.neo4j.indexName=customIndexName")
			.run(context -> {
				var properties = context.getBean(Neo4jVectorStoreProperties.class);
				assertThat(properties.getLabel()).isEqualTo("my_test_label");
				assertThat(properties.getEmbeddingDimension()).isEqualTo(384);
				assertThat(properties.getIndexName()).isEqualTo("customIndexName");

				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

				vectorStore.add(this.documents);

				assertObservationRegistry(observationRegistry, VectorStoreProvider.NEO4J,
						VectorStoreObservationContext.Operation.ADD);
				observationRegistry.clear();

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				assertObservationRegistry(observationRegistry, VectorStoreProvider.NEO4J,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				// Remove all documents from the store
				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				assertObservationRegistry(observationRegistry, VectorStoreProvider.NEO4J,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();

				results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).isEmpty();
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
