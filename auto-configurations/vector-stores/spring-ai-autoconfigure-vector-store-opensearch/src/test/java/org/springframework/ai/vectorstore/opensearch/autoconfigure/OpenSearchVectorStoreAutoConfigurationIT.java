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

package org.springframework.ai.vectorstore.opensearch.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
class OpenSearchVectorStoreAutoConfigurationIT {

	@Container
	private static final OpensearchContainer<?> opensearchContainer = new OpensearchContainer<>(
			DockerImageName.parse("opensearchproject/opensearch:2.13.0"));

	private static final String DOCUMENT_INDEX = "auto-spring-ai-document-index";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenSearchVectorStoreAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class))
		.withClassLoader(new FilteredClassLoader(Region.class, ApacheHttpClient.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.opensearch.aws.enabled=false",
				"spring.ai.vectorstore.opensearch.initialize-schema=true",
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".uris=" + opensearchContainer.getHttpHostAddress(),
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".indexName=" + DOCUMENT_INDEX,
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".mappingJson=" + """
						{
							"properties":{
								"embedding":{
									"type":"knn_vector",
									"dimension":384
								}
							}
						}
						""");

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@Test
	public void addAndSearchTest() {

		this.contextRunner.run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean(OpenSearchVectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);
			assertThat(vectorStore).isNotNull();
			assertThat(vectorStore).hasFieldOrPropertyWithValue("mappingJson", """
					{
						"properties":{
							"embedding":{
								"type":"knn_vector",
								"dimension":384
							}
						}
					}""");

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.OPENSEARCH,
					VectorStoreObservationContext.Operation.ADD);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			observationRegistry.clear();

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.OPENSEARCH,
					VectorStoreObservationContext.Operation.QUERY);

			observationRegistry.clear();

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.OPENSEARCH,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(OpenSearchVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(OpenSearchVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(OpenSearchVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(OpenSearchVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsOpensearch() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=opensearch").run(context -> {
			assertThat(context.getBeansOfType(OpenSearchVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(OpenSearchVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationWithSslBundles() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class)).run(context -> {
			assertThat(context.getBeansOfType(SslBundles.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OpenSearchClient.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OpenSearchVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(OpenSearchVectorStore.class);
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

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
