/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.milvus;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@Testcontainers
public class MilvusVectorStoreAutoConfigurationIT {

	private static DockerComposeContainer milvusContainer;

	private static final File TEMP_FOLDER = new File("target/test-" + UUID.randomUUID().toString());

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@BeforeAll
	public static void beforeAll() {
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
		TEMP_FOLDER.mkdirs();

		milvusContainer = new DockerComposeContainer(new File("src/test/resources/milvus/docker-compose.yml"))
			.withEnv("DOCKER_VOLUME_DIRECTORY", TEMP_FOLDER.getAbsolutePath())
			.withExposedService("standalone", 19530)
			.withExposedService("standalone", 9091,
					Wait.forHttp("/healthz").forPort(9091).forStatusCode(200).forStatusCode(401))
			.waitingFor("standalone", Wait.forLogMessage(".*Proxy successfully started.*\\s", 1)
				.withStartupTimeout(Duration.ofSeconds(100)));
		milvusContainer.start();
	}

	@AfterAll
	public static void afterAll() {
		milvusContainer.stop();
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MilvusVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	public void addAndSearch() {
		contextRunner
			.withPropertyValues("spring.ai.vectorstore.milvus.metricType=COSINE",
					"spring.ai.vectorstore.milvus.indexType=IVF_FLAT",
					"spring.ai.vectorstore.milvus.embeddingDimension=384",
					"spring.ai.vectorstore.milvus.collectionName=myTestCollection",

					"spring.ai.vectorstore.milvus.client.host=" + milvusContainer.getServiceHost("standalone", 19530),
					"spring.ai.vectorstore.milvus.client.port=" + milvusContainer.getServicePort("standalone", 19530))
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				vectorStore.add(documents);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				// Remove all documents from the store
				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).hasSize(0);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingClient embeddingClient() {
			return new TransformersEmbeddingClient();
		}

	}

}
