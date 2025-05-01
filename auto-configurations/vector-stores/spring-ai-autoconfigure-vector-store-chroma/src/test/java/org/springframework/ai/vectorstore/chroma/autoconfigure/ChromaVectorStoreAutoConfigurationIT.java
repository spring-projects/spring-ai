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

package org.springframework.ai.vectorstore.chroma.autoconfigure;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.springframework.ai.test.vectorstore.ObservationTestUtil.assertObservationRegistry;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
public class ChromaVectorStoreAutoConfigurationIT {

	@Container
	static ChromaDBContainer chroma = new ChromaDBContainer("ghcr.io/chroma-core/chroma:1.0.0");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations
			.of(org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.chroma.client.host=http://" + chroma.getHost(),
				"spring.ai.vectorstore.chroma.client.port=" + chroma.getMappedPort(8000),
				"spring.ai.vectorstore.chroma.collectionName=TestCollection");

	@Test
	public void addAndSearchWithFilters() {

		this.contextRunner.withPropertyValues("spring.ai.vectorstore.chroma.initializeSchema=true").run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			assertObservationRegistry(observationRegistry, VectorStoreProvider.CHROMA,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			var request = SearchRequest.builder().query("The World").topK(5).build();

			List<Document> results = vectorStore.similaritySearch(request);
			assertThat(results).hasSize(2);
			observationRegistry.clear();

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Bulgaria'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());
			observationRegistry.clear();

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Netherlands'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("chroma query")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_FILTER.asString(),
						"Expression[type=EQ, left=Key[key=country], right=Value[value=Netherlands]]")
				.hasBeenStarted()
				.hasBeenStopped();
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("chroma delete")
				.hasBeenStarted()
				.hasBeenStopped();
			observationRegistry.clear();

		});
	}

	@Test
	public void throwExceptionOnMissingCollectionAndDisabledInitializedSchema() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.chroma.initializeSchema=false")
			.run(context -> assertThatThrownBy(() -> context.getBean(VectorStore.class))
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(BeanCreationException.class)
				.hasRootCauseExactlyInstanceOf(RuntimeException.class)
				.hasRootCauseMessage(
						"Collection TestCollection doesn't exist and won't be created as the initializeSchema is set to false."));
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(ChromaVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(ChromaVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	@Disabled
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.chroma.initializeSchema=true").run(context -> {
			assertThat(context.getBeansOfType(ChromaVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(ChromaVectorStore.class);
		});
	}

	@Test
	@Disabled
	public void autoConfigurationEnabledWhenTypeIsChroma() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.type=chroma",
					"spring.ai.vectorstore.chroma.initializeSchema=true")
			.run(context -> {
				assertThat(context.getBeansOfType(ChromaVectorStoreProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
				assertThat(context.getBean(VectorStore.class)).isInstanceOf(ChromaVectorStore.class);
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

		@Bean
		public ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

	}

}
