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

package org.springframework.ai.vectorstore.elasticsearch.autoconfigure;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ElasticsearchVectorStoreAutoConfigurationIT {

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:9.2.0")
		.withEnv("xpack.security.enabled", "false");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ElasticsearchRestClientAutoConfiguration.class,
				ElasticsearchVectorStoreAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
				OpenAiEmbeddingAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.elasticsearch.uris=" + elasticsearchContainer.getHttpHostAddress(),
				"spring.ai.vectorstore.elasticsearch.initializeSchema=true",
				"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"));

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	// No parametrized test based on similarity function,
	// by default the bean will be created using cosine.
	@Test
	public void addAndSearchTest() {

		this.contextRunner.run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.ELASTICSEARCH,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			observationRegistry.clear();

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.ELASTICSEARCH,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.ELASTICSEARCH,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ElasticsearchRestClientAutoConfiguration.class,
					ElasticsearchVectorStoreAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					OpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.elasticsearch.uris=" + elasticsearchContainer.getHttpHostAddress(),
					"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
					"spring.ai.vectorstore.elasticsearch.initializeSchema=true",
					"spring.ai.vectorstore.elasticsearch.index-name=example",
					"spring.ai.vectorstore.elasticsearch.dimensions=1024",
					"spring.ai.vectorstore.elasticsearch.dense-vector-indexing=true",
					"spring.ai.vectorstore.elasticsearch.similarity=cosine",
					"spring.ai.vectorstore.elasticsearch.embedding-field-name=custom_embedding_field")
			.run(context -> {
				var properties = context.getBean(ElasticsearchVectorStoreProperties.class);
				var elasticsearchVectorStore = context.getBean(ElasticsearchVectorStore.class);

				assertThat(properties).isNotNull();
				assertThat(properties.getIndexName()).isEqualTo("example");
				assertThat(properties.getDimensions()).isEqualTo(1024);
				assertThat(properties.getSimilarity()).isEqualTo(SimilarityFunction.cosine);

				assertThat(properties.getEmbeddingFieldName()).isEqualTo("custom_embedding_field");

				assertThat(elasticsearchVectorStore).isNotNull();

				Field optionsField = ElasticsearchVectorStore.class.getDeclaredField("options");
				optionsField.setAccessible(true);
				var options = (ElasticsearchVectorStoreOptions) optionsField.get(elasticsearchVectorStore);

				assertThat(options).isNotNull();
				assertThat(options.getEmbeddingFieldName()).isEqualTo("custom_embedding_field");
			});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(ElasticsearchVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(ElasticsearchVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(ElasticsearchVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(ElasticsearchVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsElasticsearch() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=elasticsearch").run(context -> {
			assertThat(context.getBeansOfType(ElasticsearchVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(ElasticsearchVectorStore.class);
		});
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
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

	}

}
