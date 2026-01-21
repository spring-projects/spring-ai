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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geet Rawat
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class GemFireVectorStoreAutoConfigurationAuthenticationIT {

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
		.withPropertyValues("spring.ai.vectorstore.gemfire.initialize-schema=true")
		.withPropertyValues("spring.ai.vectorstore.gemfire.username=clusterManage,dataRead")
		.withPropertyValues("spring.ai.vectorstore.gemfire.password=clusterManage,dataRead")
		.withPropertyValues("spring.ai.vectorstore.gemfire.token=0123456789012345678901234567890");

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
		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-manager",
				"org.apache.geode.examples.SimpleSecurityManager");

		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-username", "clusterManage");
		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-password", "clusterManage");
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

	private Map<String, Object> parseIndex(String json) {
		try {
			JsonNode rootNode = new ObjectMapper().readTree(json);
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
