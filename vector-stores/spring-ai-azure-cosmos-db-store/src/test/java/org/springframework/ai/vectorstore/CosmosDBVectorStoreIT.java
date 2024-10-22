/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.vectorstore;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Theo van Kraay
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOSDB_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOSDB_KEY", matches = ".+")
public class CosmosDBVectorStoreIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private VectorStore vectorStore;

	@BeforeEach
	public void setup() {
		contextRunner.run(context -> {
			vectorStore = context.getBean(VectorStore.class);
		});
	}

	@Test
	public void testAddSearchAndDeleteDocuments() {

		// Create a sample document
		Document document1 = new Document(UUID.randomUUID().toString(), "Sample content1", Map.of("key1", "value1"));
		Document document2 = new Document(UUID.randomUUID().toString(), "Sample content2", Map.of("key2", "value2"));

		// Add the document to the vector store
		vectorStore.add(List.of(document1, document2));

		// create duplicate docs and assert that second one throws exception
		Document document3 = new Document(document1.getId(), "Sample content3", Map.of("key3", "value3"));
		assertThatThrownBy(() -> vectorStore.add(List.of(document3))).isInstanceOf(Exception.class)
			.hasMessageContaining("Duplicate document id: " + document1.getId());

		// Perform a similarity search
		List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Sample content").withTopK(1));

		// Verify the search results
		assertThat(results).isNotEmpty();
		assertThat(results.get(0).getId()).isEqualTo(document1.getId());

		// Remove the documents from the vector store
		vectorStore.delete(List.of(document1.getId(), document2.getId()));

		// Perform a similarity search again
		List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Sample content").withTopK(1));

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

		vectorStore.add(List.of(document1, document2, document3, document4));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World")
			.withTopK(10)
			.withFilterExpression((b.in("country", "UK", "NL")).build()));

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Document::getId).containsExactlyInAnyOrder("1", "2");

		List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("The World")
			.withTopK(10)
			.withFilterExpression(
					b.and(b.or(b.gte("year", 2021), b.eq("country", "NL")), b.ne("city", "Amsterdam")).build()));

		assertThat(results2).hasSize(1);
		assertThat(results2).extracting(Document::getId).containsExactlyInAnyOrder("1");

		List<Document> results3 = vectorStore.similaritySearch(SearchRequest.query("The World")
			.withTopK(10)
			.withFilterExpression(b.and(b.eq("country", "US"), b.eq("year", 2020)).build()));

		assertThat(results3).hasSize(1);
		assertThat(results3).extracting(Document::getId).containsExactlyInAnyOrder("4");

		vectorStore.delete(List.of(document1.getId(), document2.getId(), document3.getId(), document4.getId()));

		// Perform a similarity search again
		List<Document> results4 = vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(1));

		// Verify the search results
		assertThat(results4).isEmpty();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel,
				VectorStoreObservationConvention convention) {
			CosmosDBVectorStoreConfig config = new CosmosDBVectorStoreConfig();
			config.setDatabaseName("test-database");
			config.setContainerName("test-container");
			config.setMetadataFields("country,year,city");
			config.setVectorStoreThoughput(1000);
			return new CosmosDBVectorStore(null, convention, cosmosClient, config, embeddingModel);

		}

		@Bean
		public CosmosAsyncClient cosmosClient() {
			return new CosmosClientBuilder().endpoint(System.getenv("AZURE_COSMOSDB_ENDPOINT"))
				.key(System.getenv("AZURE_COSMOSDB_KEY"))
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
