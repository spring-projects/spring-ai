/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.milvus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@Testcontainers
public class MilvusVectorStoreAutoConfigurationIT {

	private static final Network network = Network.newNetwork();

	private static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2023-11-11T08-14-41Z")
		.withNetwork(network)
		.withNetworkAliases("minio");

	private static final GenericContainer<?> etcd = new GenericContainer<>("quay.io/coreos/etcd:v3.5.5")
		.withNetwork(network)
		.withNetworkAliases("etcd")
		.withCommand("etcd", "-advertise-client-urls=http://127.0.0.1:2379", "-listen-client-urls=http://0.0.0.0:2379",
				"--data-dir=/etcd")
		.withEnv(Map.of("ETCD_AUTO_COMPACTION_MODE", "revision", "ETCD_AUTO_COMPACTION_RETENTION", "1000",
				"ETCD_QUOTA_BACKEND_BYTES", "4294967296", "ETCD_SNAPSHOT_COUNT", "50000"))
		.waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1));

	@Container
	private static final GenericContainer<?> milvus = new GenericContainer<>("milvusdb/milvus:v2.3.1")
		.withExposedPorts(19530)
		.dependsOn(minio, etcd)
		.withNetwork(network)
		.withCommand("milvus", "run", "standalone")
		.withEnv(Map.of("ETCD_ENDPOINTS", "etcd:2379", "MINIO_ADDRESS", "minio:9000"))
		.waitingFor(Wait.forLogMessage(".*Proxy successfully started.*\\s", 1));

	private static final File TEMP_FOLDER = new File("target/test-" + UUID.randomUUID().toString());

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeAll
	public static void beforeAll() {
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
		TEMP_FOLDER.mkdirs();
	}

	@AfterAll
	public static void afterAll() {
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

					"spring.ai.vectorstore.milvus.client.host=" + milvus.getHost(),
					"spring.ai.vectorstore.milvus.client.port=" + milvus.getMappedPort(19530))
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
