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

package org.springframework.ai.vectorstore.typesense.autoconfigure;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.typesense.TypesenseContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.typesense.TypesenseVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
public class TypesenseVectorStoreAutoConfigurationIT {

	@Container
	private static final TypesenseContainer typesense = new TypesenseContainer("typesense/typesense:26.0");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TypesenseVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	public void addAndSearch() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.typesense.embeddingDimension=384",
					"spring.ai.vectorstore.typesense.collectionName=myTestCollection",
					"spring.ai.vectorstore.typesense.initialize-schema=true",
					"spring.ai.vectorstore.typesense.client.apiKey=" + typesense.getApiKey(),
					"spring.ai.vectorstore.typesense.client.protocol=http",
					"spring.ai.vectorstore.typesense.client.host=" + typesense.getHost(),
					"spring.ai.vectorstore.typesense.client.port=" + typesense.getHttpPort())
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

				vectorStore.add(this.documents);

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.TYPESENSE,
						VectorStoreObservationContext.Operation.ADD);
				observationRegistry.clear();

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.TYPESENSE,
						VectorStoreObservationContext.Operation.QUERY);
				observationRegistry.clear();

				vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

				ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.TYPESENSE,
						VectorStoreObservationContext.Operation.DELETE);
				observationRegistry.clear();

				results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).hasSize(0);
			});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(TypesenseVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(TypesenseVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(TypesenseVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(TypesenseVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsTypesense() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=typesense").run(context -> {
			assertThat(context.getBeansOfType(TypesenseVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(TypesenseVectorStore.class);
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
