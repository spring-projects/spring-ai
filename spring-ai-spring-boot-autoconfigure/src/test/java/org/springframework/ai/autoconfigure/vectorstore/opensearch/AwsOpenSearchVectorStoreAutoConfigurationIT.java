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

package org.springframework.ai.autoconfigure.vectorstore.opensearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.OpenSearchVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
class AwsOpenSearchVectorStoreAutoConfigurationIT {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.5.0"))
		.withEnv("LOCALSTACK_HOST", "localhost.localstack.cloud");

	private static final String DOCUMENT_INDEX = "auto-spring-ai-document-index";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenSearchVectorStoreAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.opensearch.initialize-schema=true")
		.withPropertyValues(
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".aws.host="
						+ String.format("testcontainers-domain.%s.opensearch.localhost.localstack.cloud:%s",
								localstack.getRegion(), localstack.getMappedPort(4566)),
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".aws.service-name=opensearch",
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".aws.region=" + localstack.getRegion(),
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".aws.access-key=" + localstack.getAccessKey(),
				OpenSearchVectorStoreProperties.CONFIG_PREFIX + ".aws.secret-key=" + localstack.getSecretKey(),
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

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		String[] createDomainCmd = { "awslocal", "opensearch", "create-domain", "--domain-name",
				"testcontainers-domain", "--region", localstack.getRegion() };
		localstack.execInContainer(createDomainCmd);

		String[] describeDomainCmd = { "awslocal", "opensearch", "describe-domain", "--domain-name",
				"testcontainers-domain", "--region", localstack.getRegion() };
		await().pollInterval(Duration.ofSeconds(30)).atMost(Duration.ofSeconds(300)).untilAsserted(() -> {
			org.testcontainers.containers.Container.ExecResult execResult = localstack
				.execInContainer(describeDomainCmd);
			String response = execResult.getStdout();
			JSONArray processed = JsonPath.read(response, "$.DomainStatus[?(@.Processing == false)]");
			assertThat(processed).isNotEmpty();
		});
	}

	@Test
	public void addAndSearchTest() {

		this.contextRunner.run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean(OpenSearchVectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
						hasSize(0));
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
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
