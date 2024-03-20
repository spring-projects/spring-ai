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
package org.springframework.ai.autoconfigure.vectorstore.elasticsearch;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ElasticsearchVectorStoreAutoConfigurationIT {

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:8.12.2")
		.withEnv("xpack.security.enabled", "false");

	private static final String DEFAULT = "default cosine similarity";

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ElasticsearchRestClientAutoConfiguration.class,
				ElasticsearchVectorStoreAutoConfiguration.class, RestClientAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class, OpenAiAutoConfiguration.class))
		.withPropertyValues("spring.elasticsearch.uris=" + elasticsearchContainer.getHttpHostAddress(),
				"spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"));

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, """
			  double value = dotProduct(params.query_vector, 'embedding');
			  return sigmoid(1, Math.E, -value);
			""", "1 / (1 + l1norm(params.query_vector, 'embedding'))",
			"1 / (1 + l2norm(params.query_vector, 'embedding'))" })
	public void addAndSearchTest(String similarityFunction) {

		this.contextRunner.run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);

			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

			vectorStore.add(documents);

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0)),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Great Depression").withTopK(1).withSimilarityThreshold(0));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).toList());

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

}
