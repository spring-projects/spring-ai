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

package org.springframework.ai.vectorstore.weaviate.autoconfigure;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.MetadataField;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.test.vectorstore.ObservationTestUtil.assertObservationRegistry;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
public class WeaviateVectorStoreAutoConfigurationIT {

	@Container
	static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:1.25.4")
		.waitingFor(Wait.forHttp("/v1/.well-known/ready").forPort(8080));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(WeaviateVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.weaviate.scheme=http",
				"spring.ai.vectorstore.weaviate.host=" + weaviate.getHttpHostAddress(),
				"spring.ai.vectorstore.weaviate.filter-field.country=TEXT",
				"spring.ai.vectorstore.weaviate.filter-field.year=NUMBER",
				"spring.ai.vectorstore.weaviate.filter-field.active=BOOLEAN",
				"spring.ai.vectorstore.weaviate.filter-field.price=NUMBER");

	@Test
	public void addAndSearchWithFilters() {

		this.contextRunner.run(context -> {

			WeaviateVectorStoreProperties properties = context.getBean(WeaviateVectorStoreProperties.class);

			assertThat(properties.getFilterField()).hasSize(4);

			assertThat(properties.getFilterField().get("country")).isEqualTo(MetadataField.Type.TEXT);
			assertThat(properties.getFilterField().get("year")).isEqualTo(MetadataField.Type.NUMBER);
			assertThat(properties.getFilterField().get("active")).isEqualTo(MetadataField.Type.BOOLEAN);
			assertThat(properties.getFilterField().get("price")).isEqualTo(MetadataField.Type.NUMBER);

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria", "price", 3.14, "active", true, "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands", "price", 1.57, "active", false, "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument));

			assertObservationRegistry(observationRegistry, VectorStoreProvider.WEAVIATE,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			var request = SearchRequest.builder().query("The World").topK(5).build();

			List<Document> results = vectorStore.similaritySearch(request);
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Bulgaria'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			assertObservationRegistry(observationRegistry, VectorStoreProvider.WEAVIATE,
					VectorStoreObservationContext.Operation.QUERY);

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Netherlands'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("price > 1.57 && active == true")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("year in [2020, 2023]")
				.build());
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("year > 2020 && year <= 2023")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());

			assertObservationRegistry(observationRegistry, VectorStoreProvider.WEAVIATE,
					VectorStoreObservationContext.Operation.DELETE);
		});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(WeaviateVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(WeaviateVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(WeaviateVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(WeaviateVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsWeaviate() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=weaviate").run(context -> {
			assertThat(context.getBeansOfType(WeaviateVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(WeaviateVectorStore.class);
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
