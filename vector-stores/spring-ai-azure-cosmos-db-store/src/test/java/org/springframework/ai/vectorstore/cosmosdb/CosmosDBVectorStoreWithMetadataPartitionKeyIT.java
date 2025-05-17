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

package org.springframework.ai.vectorstore.cosmosdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Theo van Kraay
 * @author Thomas Vitale
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOSDB_ENDPOINT", matches = ".+")
public class CosmosDBVectorStoreWithMetadataPartitionKeyIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private VectorStore vectorStore;

	@BeforeEach
	public void setup() {
		this.contextRunner.run(context -> this.vectorStore = context.getBean(VectorStore.class));
	}

	@Test
	public void testAddSearchAndDeleteDocuments() {

		// Create a sample document
		Document document1 = new Document(UUID.randomUUID().toString(), "Sample content1", Map.of("key1", "value1"));
		assertThatThrownBy(() -> this.vectorStore.add(List.of(document1))).isInstanceOf(Exception.class)
			.hasMessageContaining("Partition key 'country' not found in document metadata.");

		Document document2 = new Document(UUID.randomUUID().toString(), "Sample content1", Map.of("country", "UK"));
		this.vectorStore.add(List.of(document2));

		// Perform a similarity search
		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("Sample content1").topK(1).build());

		// Verify the search results
		assertThat(results).isNotEmpty();
		assertThat(results.get(0).getId()).isEqualTo(document2.getId());

		// Remove the documents from the vector store
		this.vectorStore.delete(List.of(document2.getId()));

		// Perform a similarity search again
		List<Document> results2 = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("Sample content").topK(1).build());

		// Verify the search results
		assertThat(results2).isEmpty();

	}

	@Test
	void testSimilaritySearchWithFilter() {

		// Insert documents using vectorStore.add
		Map<String, Object> metadata1;
		metadata1 = new HashMap<>();
		metadata1.put("country", "UK");
		metadata1.put("year", 2021);
		metadata1.put("city", "London");

		Map<String, Object> metadata2;
		metadata2 = new HashMap<>();
		metadata2.put("country", "NL");
		metadata2.put("year", 2022);
		metadata2.put("city", "Amsterdam");

		Map<String, Object> metadata3;
		metadata3 = new HashMap<>();
		metadata3.put("country", "US");
		metadata3.put("year", 2019);
		metadata3.put("city", "Sofia");

		Map<String, Object> metadata4;
		metadata4 = new HashMap<>();
		metadata4.put("country", "US");
		metadata4.put("year", 2020);
		metadata4.put("city", "Sofia");

		Document document1 = new Document("1", "A document about the UK", metadata1);
		Document document2 = new Document("2", "A document about the Netherlands", metadata2);
		Document document3 = new Document("3", "A document about the US", metadata3);
		Document document4 = new Document("4", "A document about the US", metadata4);

		this.vectorStore.add(List.of(document1, document2, document3, document4));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder()
			.query("The World")
			.topK(10)
			.filterExpression((b.in("country", "UK", "NL")).build())
			.build());

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Document::getId).containsExactlyInAnyOrder("1", "2");
		for (Document doc : results) {
			assertThat(doc.getMetadata().get("country")).isIn("UK", "NL");
			assertThat(doc.getMetadata().get("year")).isIn(2021, 2022);
			assertThat(doc.getMetadata().get("city")).isIn("London", "Amsterdam").isNotEqualTo("Sofia");
		}

		List<Document> results2 = this.vectorStore.similaritySearch(SearchRequest.builder()
			.query("The World")
			.topK(10)
			.filterExpression(
					b.and(b.or(b.gte("year", 2021), b.eq("country", "NL")), b.ne("city", "Amsterdam")).build())
			.build());

		assertThat(results2).hasSize(1);
		assertThat(results2).extracting(Document::getId).containsExactlyInAnyOrder("1");

		List<Document> results3 = this.vectorStore.similaritySearch(SearchRequest.builder()
			.query("The World")
			.topK(10)
			.filterExpression(b.and(b.eq("country", "US"), b.eq("year", 2020)).build())
			.build());

		assertThat(results3).hasSize(1);
		assertThat(results3).extracting(Document::getId).containsExactlyInAnyOrder("4");

		this.vectorStore.delete(List.of(document1.getId(), document2.getId(), document3.getId(), document4.getId()));

		// Perform a similarity search again
		List<Document> results4 = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("The World").topK(1).build());

		// Verify the search results
		assertThat(results4).isEmpty();
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			CosmosDBVectorStore vectorStore = context.getBean(CosmosDBVectorStore.class);
			Optional<CosmosAsyncContainer> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel,
				VectorStoreObservationConvention convention) {
			return CosmosDBVectorStore.builder(cosmosClient, embeddingModel)
				.databaseName("test-database")
				.containerName("test-container-metadata-partition-key")
				.metadataFields(List.of("country", "year", "city"))
				.partitionKeyPath("/metadata/country")
				.vectorStoreThroughput(1000)
				.customObservationConvention(convention)
				.build();
		}

		@Bean
		public CosmosAsyncClient cosmosClient() {
			return new CosmosClientBuilder().endpoint(System.getenv("AZURE_COSMOSDB_ENDPOINT"))
				.credential(new DefaultAzureCredentialBuilder().build())
				.userAgentSuffix("SpringAI-CDBNoSQL-VectorStore")
				.gatewayMode()
				.buildAsyncClient();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		public VectorStoreObservationConvention observationConvention() {
			// Replace with an actual observation convention or a mock if needed
			return new VectorStoreObservationConvention() {

			};
		}

	}

}
