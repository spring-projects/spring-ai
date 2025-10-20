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

package org.springframework.ai.vectorstore.gemfire;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author Jason Huynh
 */
public class GemFireWithBasicAuthenticationVectorStoreIT extends GemFireVectorStoreBaseIT {

	@AfterAll
	public static void stopGemFireCluster() {
		gemFireCluster.close();
	}

	@BeforeAll
	public static void startGemFireCluster() {
		Ports.Binding hostPort = Ports.Binding.bindPort(HTTP_SERVICE_PORT);
		ExposedPort exposedPort = new ExposedPort(HTTP_SERVICE_PORT);
		PortBinding mappedPort = new PortBinding(hostPort, exposedPort);
		gemFireCluster = new GemFireCluster(GemFireImage.DEFAULT_IMAGE, LOCATOR_COUNT, SERVER_COUNT);
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

	@Override
	Class getTestApplicationClass() {
		return TestApplication.class;
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public GemFireVectorStore vectorStore(EmbeddingModel embeddingModel) {
			return GemFireVectorStore.builder(embeddingModel)
				.host("localhost")
				.port(HTTP_SERVICE_PORT)
				.username("cluster,data")
				.password("cluster,data")
				.indexName(INDEX_NAME)
				.fields(new String[] { "year", "country", "activationDate" })
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
