/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.qdrant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test using a free tier Qdrant Cloud instance: https://cloud.qdrant.io
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 0.8.1
 */
// NOTE: The free Qdrant Cluster and the QDRANT_API_KEY expire after 4 weeks of
// inactivity.
@EnabledIfEnvironmentVariable(named = "QDRANT_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "QDRANT_HOST", matches = ".+")
public class QdrantVectorStoreCloudAutoConfigurationIT {

	private static final String COLLECTION_NAME = "test_collection";

	// Because we pre-create the collection.
	private static final int EMBEDDING_DIMENSION = 384;

	private static final String CLOUD_API_KEY = System.getenv("QDRANT_API_KEY");

	private static final String CLOUD_HOST = System.getenv("QDRANT_HOST");

	// NOTE: The GRPC port (usually 6334) is different from the HTTP port (usually 6333)!
	private static final int CLOUD_GRPC_PORT = 6334;

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@BeforeAll
	static void setup() throws InterruptedException, ExecutionException {

		// Create a new test collection
		try (QdrantClient client = new QdrantClient(
				QdrantGrpcClient.newBuilder(CLOUD_HOST, CLOUD_GRPC_PORT, true).withApiKey(CLOUD_API_KEY).build())) {

			if (client.listCollectionsAsync().get().stream().anyMatch(c -> c.equals(COLLECTION_NAME))) {
				client.deleteCollectionAsync(COLLECTION_NAME).get();
			}

			var vectorParams = VectorParams.newBuilder()
				.setDistance(Distance.Cosine)
				.setSize(EMBEDDING_DIMENSION)
				.build();

			client.createCollectionAsync(COLLECTION_NAME, vectorParams).get();
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(QdrantVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.qdrant.port=" + CLOUD_GRPC_PORT,
				"spring.ai.vectorstore.qdrant.host=" + CLOUD_HOST,
				"spring.ai.vectorstore.qdrant.api-key=" + CLOUD_API_KEY,
				"spring.ai.vectorstore.qdrant.collection-name=" + COLLECTION_NAME,
				"spring.ai.vectorstore.qdrant.initializeSchema=true", "spring.ai.vectorstore.qdrant.use-tls=true");

	@Test
	public void addAndSearch() {
		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("What is Great Depression?").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getMetadata()).containsKeys("depression", "distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());
			results = vectorStore.similaritySearch(SearchRequest.query("Great Depression").withTopK(1));
			assertThat(results).hasSize(0);
		});
	}

	public static String getText(String uri) {
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
