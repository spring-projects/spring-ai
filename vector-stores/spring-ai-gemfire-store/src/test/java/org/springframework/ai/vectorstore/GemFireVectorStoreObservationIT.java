/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 */
public class GemFireVectorStoreObservationIT {

	public static final String TEST_INDEX_NAME = "spring-ai-index1";

	private static GemFireCluster gemFireCluster;

	private static final int HTTP_SERVICE_PORT = 9090;

	private static final int LOCATOR_COUNT = 1;

	private static final int SERVER_COUNT = 1;

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

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void observationVectorStoreAddAndQueryOperations() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("gemfire add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "add")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.GEMFIRE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), TEST_INDEX_NAME)
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_NAMESPACE.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString(), "/embeddings")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_SIMILARITY_METRIC.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
						"none")

				.hasBeenStarted()
				.hasBeenStopped();

			Awaitility.await()
				.atMost(1, MINUTES)
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(5).withSimilarityThresholdAll()),
						hasSize(3));

			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression").withTopK(1));

			assertThat(results).isNotEmpty();

			TestObservationRegistryAssert.assertThat(observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation()
				.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
				.that()
				.hasContextualNameEqualTo("gemfire query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query")
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.DB_SYSTEM.asString(),
						VectorStoreProvider.GEMFIRE.value())
				.hasLowCardinalityKeyValue(LowCardinalityKeyNames.SPRING_AI_KIND.asString(),
						SpringAiKind.VECTOR_STORE.value())

				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_CONTENT.asString(),
						"What is Great Depression")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_DIMENSION_COUNT.asString(), "384")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_COLLECTION_NAME.asString(), TEST_INDEX_NAME)
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_NAMESPACE.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_FIELD_NAME.asString(), "/embeddings")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_SIMILARITY_METRIC.asString(), "none")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_TOP_K.asString(), "1")
				.hasHighCardinalityKeyValue(HighCardinalityKeyNames.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.asString(),
						"0.0")

				.hasBeenStarted()
				.hasBeenStopped();

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public GemFireVectorStore vectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry) {
			return new GemFireVectorStore(GemFireVectorStore.GemFireVectorStoreConfig.builder()
				.setHost("localhost")
				.setPort(HTTP_SERVICE_PORT)
				.setIndexName(TEST_INDEX_NAME)
				.build(), embeddingModel, true, observationRegistry, null, new TokenCountBatchingStrategy());
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
