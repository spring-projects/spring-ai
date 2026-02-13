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

package org.springframework.ai.vectorstore.opensearch;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpHost;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OLLAMA_TESTS_ENABLED", matches = "true")
class OpenSearchVectorStoreWithOllamaIT {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	private static final int DEFAULT_MAX_RETRIES = 2;

	private static final String OLLAMA_LOCAL_URL = "http://localhost:11434";

	@Container
	private static final OpenSearchContainer<?> opensearchContainer = new OpenSearchContainer<>(
			OpenSearchImage.DEFAULT_IMAGE);

	private static final String DEFAULT = "cosinesimil";

	private List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));

		// Ensure the model is pulled before running tests
		ensureModelIsPresent(OllamaModel.MXBAI_EMBED_LARGE.getName());
	}

	private static void ensureModelIsPresent(final String model) {
		final OllamaApi api = OllamaApi.builder().baseUrl(OLLAMA_LOCAL_URL).build();
		final var modelManagementOptions = ModelManagementOptions.builder()
			.maxRetries(DEFAULT_MAX_RETRIES)
			.timeout(DEFAULT_TIMEOUT)
			.build();
		final var ollamaModelManager = new OllamaModelManager(api, modelManagementOptions);
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
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

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
	}

	@BeforeEach
	void cleanDatabase() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);
			vectorStore.delete(List.of("_all"));

			VectorStore anotherVectorStore = context.getBean("anotherVectorStore", OpenSearchVectorStore.class);
			anotherVectorStore.delete(List.of("_all"));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { DEFAULT, "l1", "l2", "linf" })
	public void addAndSearchTest(String similarityFunction) {

		getContextRunner().run(context -> {
			OpenSearchVectorStore vectorStore = context.getBean("vectorStore", OpenSearchVectorStore.class);

			if (!DEFAULT.equals(similarityFunction)) {
				vectorStore.withSimilarityFunction(similarityFunction);
			}

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		@Qualifier("vectorStore")
		public OpenSearchVectorStore vectorStore(EmbeddingModel embeddingModel) {
			try {
				OpenSearchClient openSearchClient = new OpenSearchClient(ApacheHttpClient5TransportBuilder
					.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
					.build());
				return OpenSearchVectorStore.builder(openSearchClient, embeddingModel).initializeSchema(true).build();
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		@Qualifier("anotherVectorStore")
		public OpenSearchVectorStore anotherVectorStore(EmbeddingModel embeddingModel) {
			try {
				OpenSearchClient openSearchClient = new OpenSearchClient(ApacheHttpClient5TransportBuilder
					.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
					.build());
				return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
					.index("another_index")
					.mappingJson(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION)
					.initializeSchema(true)
					.build();
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return OllamaEmbeddingModel.builder()
				.ollamaApi(OllamaApi.builder().build())
				.defaultOptions(OllamaEmbeddingOptions.builder().model(OllamaModel.MXBAI_EMBED_LARGE).build())
				.build();
		}

	}

}
