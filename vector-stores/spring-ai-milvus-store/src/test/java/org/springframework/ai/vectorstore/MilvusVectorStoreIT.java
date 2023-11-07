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

package org.springframework.ai.vectorstore;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@Testcontainers
public class MilvusVectorStoreIT {

	private static DockerComposeContainer milvusContainer;

	private static final File TEMP_FOLDER = new File("target/test-" + UUID.randomUUID().toString());

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	@BeforeAll
	public static void beforeAll() {
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
		TEMP_FOLDER.mkdirs();

		milvusContainer = new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
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

	private void resetCollection(VectorStore vectorStore) {
		((MilvusVectorStore) vectorStore).dropCollection();
		((MilvusVectorStore) vectorStore).createCollection();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "L2", "IP" })
	public void addAndSearch(String metricType) {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(documents);

				List<Document> results = vectorStore.similaritySearch("Great", 1);

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
				assertThat(resultDoc.getContent()).isEqualTo(
						"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKey("meta2");
				assertThat(resultDoc.getMetadata()).containsKey("distance");

				// Remove all documents from the store
				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				List<Document> results2 = vectorStore.similaritySearch("Hello", 1);
				assertThat(results2).hasSize(0);
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "L2" })
	// @ValueSource(strings = { "IP", "L2" })
	public void searchWithFilters(String metricType) throws InterruptedException {

		// https://milvus.io/docs/json_data_type.md

		final double THRESHOLD_ALL = 0.0;

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", 2023));

				vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = vectorStore.similaritySearch("The World", 5);
				assertThat(results).hasSize(3);

				results = vectorStore.similaritySearch("The World", 5, THRESHOLD_ALL, "country == 'NL'");
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = vectorStore.similaritySearch("The World", 5, THRESHOLD_ALL, "country == 'BG'");
				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = vectorStore.similaritySearch("The World", 5, THRESHOLD_ALL,
						"country == 'BG' && year == 2020");
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());
			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "L2", "IP" })
	public void documentUpdate(String metricType) {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				vectorStore.add(List.of(document));

				List<Document> results = vectorStore.similaritySearch("Spring", 5);

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKey("meta1");
				assertThat(resultDoc.getMetadata()).containsKey("distance");

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				vectorStore.add(List.of(sameIdDocument));

				results = vectorStore.similaritySearch("FooBar", 5);

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKey("meta2");
				assertThat(resultDoc.getMetadata()).containsKey("distance");

				vectorStore.delete(List.of(document.getId()));

			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "L2", "IP" })
	public void searchWithThreshold(String metricType) {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType)
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(documents);

				List<Document> fullResult = vectorStore.similaritySearch("Great", 5, 0.0);

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get("distance"))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = vectorStore.similaritySearch("Great", 5, (1 - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
				assertThat(resultDoc.getContent()).isEqualTo(
						"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
				assertThat(resultDoc.getMetadata()).containsKey("meta2");
				assertThat(resultDoc.getMetadata()).containsKey("distance");

			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Value("${test.spring.ai.vectorstore.milvus.metricType}")
		private MetricType metricType;

		@Bean
		public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingClient embeddingClient) {
			MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder()
				.withCollectionName("test_vector_store")
				.withDatabaseName("default")
				.withIndexType(IndexType.IVF_FLAT)
				.withMetricType(metricType)
				.build();
			return new MilvusVectorStore(milvusClient, embeddingClient, config);
		}

		@Bean
		public MilvusServiceClient milvusClient() {
			return new MilvusServiceClient(ConnectParam.newBuilder()
				.withHost("localhost")
				.withPort(milvusContainer.getServicePort("standalone", 19530))
				.build());
		}

	}

}