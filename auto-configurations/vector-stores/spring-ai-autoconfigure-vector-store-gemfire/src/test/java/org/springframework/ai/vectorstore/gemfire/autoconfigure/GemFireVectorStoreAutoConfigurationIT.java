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

package org.springframework.ai.vectorstore.gemfire.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Geet Rawat
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class GemFireVectorStoreAutoConfigurationIT {

	private static final String INDEX_NAME = "spring-ai-index";

	private static final int BEAM_WIDTH = 50;

	private static final int MAX_CONNECTIONS = 8;

	private static final String SIMILARITY_FUNCTION = "DOT_PRODUCT";

	private static final String[] FIELDS = { "someField1", "someField2" };

	private static final int BUCKET_COUNT = 2;

	private static final int HTTP_SERVICE_PORT = 9090;

	private static final int LOCATOR_COUNT = 1;

	private static final int SERVER_COUNT = 1;

	private static GemFireCluster gemFireCluster;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GemFireVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.gemfire.index-name=" + INDEX_NAME)
		.withPropertyValues("spring.ai.vectorstore.gemfire.beam-width=" + BEAM_WIDTH)
		.withPropertyValues("spring.ai.vectorstore.gemfire.max-connections=" + MAX_CONNECTIONS)
		.withPropertyValues("spring.ai.vectorstore.gemfire.vector-similarity-function=" + SIMILARITY_FUNCTION)
		.withPropertyValues("spring.ai.vectorstore.gemfire.buckets=" + BUCKET_COUNT)
		.withPropertyValues("spring.ai.vectorstore.gemfire.fields=someField1,someField2")
		.withPropertyValues("spring.ai.vectorstore.gemfire.host=localhost")
		.withPropertyValues("spring.ai.vectorstore.gemfire.port=" + HTTP_SERVICE_PORT)
		.withPropertyValues("spring.ai.vectorstore.gemfire.initialize-schema=true");

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@AfterAll
	public static void stopGemFireCluster() {
		gemFireCluster.close();
	}

	@BeforeAll
	public static void startGemFireCluster() {
		Ports.Binding hostPort = Ports.Binding.bindPort(HTTP_SERVICE_PORT);
		ExposedPort exposedPort = new ExposedPort(HTTP_SERVICE_PORT);
		PortBinding mappedPort = new PortBinding(hostPort, exposedPort);
		gemFireCluster = new GemFireCluster("gemfire/gemfire-all:10.1-jdk17", LOCATOR_COUNT, SERVER_COUNT);
		gemFireCluster.withConfiguration(GemFireCluster.SERVER_GLOB,
				container -> container.withExposedPorts(HTTP_SERVICE_PORT)
					.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(mappedPort)));
		gemFireCluster.withGemFireProperty(GemFireCluster.SERVER_GLOB, "http-service-port",
				Integer.toString(HTTP_SERVICE_PORT));
		gemFireCluster.acceptLicense().start();

		System.setProperty("spring.data.gemfire.pool.locators",
				String.format("localhost[%d]", gemFireCluster.getLocatorPort()));
	}

	@Test
	void ensureGemFireVectorStoreCustomConfiguration() {
		this.contextRunner.run(context -> {
			GemFireVectorStore store = context.getBean(GemFireVectorStore.class);

			Assertions.assertNotNull(store);
			assertThat(store.getIndexName()).isEqualTo(INDEX_NAME);
			assertThat(store.getBeamWidth()).isEqualTo(BEAM_WIDTH);
			assertThat(store.getMaxConnections()).isEqualTo(MAX_CONNECTIONS);
			assertThat(store.getVectorSimilarityFunction()).isEqualTo(SIMILARITY_FUNCTION);
			assertThat(store.getFields()).isEqualTo(FIELDS);

			String indexJson = store.getIndex();
			Map<String, Object> index = parseIndex(indexJson);
			assertThat(index.get("name")).isEqualTo(INDEX_NAME);
			assertThat(index.get("beam-width")).isEqualTo(BEAM_WIDTH);
			assertThat(index.get("max-connections")).isEqualTo(MAX_CONNECTIONS);
			assertThat(index.get("vector-similarity-function")).isEqualTo(SIMILARITY_FUNCTION);
			assertThat(index.get("buckets")).isEqualTo(BUCKET_COUNT);

		});
	}

	@Test
	public void addAndSearchTest() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.GEMFIRE,
					VectorStoreObservationContext.Operation.ADD);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build()),
						hasSize(1));
			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.GEMFIRE,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.GEMFIRE,
					VectorStoreObservationContext.Operation.DELETE);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build()),
						hasSize(0));
			observationRegistry.clear();
		});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(GemFireVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(GemFireVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(GemFireVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(GemFireVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsGemfire() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=gemfire").run(context -> {
			assertThat(context.getBeansOfType(GemFireVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(GemFireVectorStore.class);
		});
	}

	private Map<String, Object> parseIndex(String json) {
		try {
			JsonNode rootNode = JsonMapper.shared().readTree(json);
			Map<String, Object> indexDetails = new HashMap<>();
			if (rootNode.isObject()) {
				if (rootNode.has("name")) {
					indexDetails.put("name", rootNode.get("name").asText());
				}
				if (rootNode.has("beam-width")) {
					indexDetails.put("beam-width", rootNode.get("beam-width").asInt());
				}
				if (rootNode.has("max-connections")) {
					indexDetails.put("max-connections", rootNode.get("max-connections").asInt());
				}
				if (rootNode.has("vector-similarity-function")) {
					indexDetails.put("vector-similarity-function", rootNode.get("vector-similarity-function").asText());
				}
				if (rootNode.has("buckets")) {
					indexDetails.put("buckets", rootNode.get("buckets").asInt());
				}
				if (rootNode.has("number-of-embeddings")) {
					indexDetails.put("number-of-embeddings", rootNode.get("number-of-embeddings").asInt());
				}
			}
			return indexDetails;
		}
		catch (Exception e) {
			return new HashMap<>();
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
