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

package org.springframework.ai.vectorstore.redis;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the RedisVectorStore with different distance metrics.
 */
@Testcontainers
class RedisVectorStoreDistanceMetricIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort());

	@BeforeEach
	void cleanDatabase() {
		// Clean Redis completely before each test
		JedisPooled jedis = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		jedis.flushAll();
	}

	@Test
	void cosineDistanceMetric() {
		// Create a vector store with COSINE distance metric
		this.contextRunner.run(context -> {
			// Get the base Jedis client for creating a custom store
			JedisPooled jedis = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			// Create the vector store with explicit COSINE distance metric
			RedisVectorStore vectorStore = RedisVectorStore.builder(jedis, embeddingModel)
				.indexName("cosine-test-index")
				.distanceMetric(RedisVectorStore.DistanceMetric.COSINE) // New feature
				.metadataFields(MetadataField.tag("category"))
				.initializeSchema(true)
				.build();

			// Test basic functionality with the configured distance metric
			testVectorStoreWithDocuments(vectorStore);
		});
	}

	@Test
	void l2DistanceMetric() {
		// Create a vector store with L2 distance metric
		this.contextRunner.run(context -> {
			// Get the base Jedis client for creating a custom store
			JedisPooled jedis = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			// Create the vector store with explicit L2 distance metric
			RedisVectorStore vectorStore = RedisVectorStore.builder(jedis, embeddingModel)
				.indexName("l2-test-index")
				.distanceMetric(RedisVectorStore.DistanceMetric.L2)
				.metadataFields(MetadataField.tag("category"))
				.initializeSchema(true)
				.build();

			// Initialize the vector store schema
			vectorStore.afterPropertiesSet();

			// Add test documents first
			List<Document> documents = List.of(
					new Document("Document about artificial intelligence and machine learning",
							Map.of("category", "AI")),
					new Document("Document about databases and storage systems", Map.of("category", "DB")),
					new Document("Document about neural networks and deep learning", Map.of("category", "AI")));

			vectorStore.add(documents);

			// Test L2 distance metric search with AI query
			List<Document> aiResults = vectorStore
				.similaritySearch(SearchRequest.builder().query("AI machine learning").topK(10).build());

			// Verify we get relevant AI results
			assertThat(aiResults).isNotEmpty();
			assertThat(aiResults).hasSizeGreaterThanOrEqualTo(2); // We have 2 AI
																	// documents

			// The first result should be about AI (closest match)
			Document topResult = aiResults.get(0);
			assertThat(topResult.getMetadata()).containsEntry("category", "AI");
			assertThat(topResult.getText()).containsIgnoringCase("artificial intelligence");

			// Test with database query
			List<Document> dbResults = vectorStore
				.similaritySearch(SearchRequest.builder().query("database systems").topK(10).build());

			// Verify we get results and at least one contains database content
			assertThat(dbResults).isNotEmpty();

			// Find the database document in the results (might not be first with L2
			// distance)
			boolean foundDbDoc = false;
			for (Document doc : dbResults) {
				if (doc.getText().toLowerCase().contains("databases")
						&& "DB".equals(doc.getMetadata().get("category"))) {
					foundDbDoc = true;
					break;
				}
			}
			assertThat(foundDbDoc).as("Should find the database document in results").isTrue();
		});
	}

	@Test
	void ipDistanceMetric() {
		// Create a vector store with IP distance metric
		this.contextRunner.run(context -> {
			// Get the base Jedis client for creating a custom store
			JedisPooled jedis = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

			// Create the vector store with explicit IP distance metric
			RedisVectorStore vectorStore = RedisVectorStore.builder(jedis, embeddingModel)
				.indexName("ip-test-index")
				.distanceMetric(RedisVectorStore.DistanceMetric.IP) // New feature
				.metadataFields(MetadataField.tag("category"))
				.initializeSchema(true)
				.build();

			// Test basic functionality with the configured distance metric
			testVectorStoreWithDocuments(vectorStore);
		});
	}

	private void testVectorStoreWithDocuments(VectorStore vectorStore) {
		// Ensure schema initialization (using afterPropertiesSet)
		if (vectorStore instanceof RedisVectorStore redisVectorStore) {
			redisVectorStore.afterPropertiesSet();

			// Verify index exists
			JedisPooled jedis = redisVectorStore.getJedis();
			Set<String> indexes = jedis.ftList();

			// The index name is set in the builder, so we should verify it exists
			assertThat(indexes).isNotEmpty();
			assertThat(indexes).hasSizeGreaterThan(0);
		}

		// Add test documents
		List<Document> documents = List.of(
				new Document("Document about artificial intelligence and machine learning", Map.of("category", "AI")),
				new Document("Document about databases and storage systems", Map.of("category", "DB")),
				new Document("Document about neural networks and deep learning", Map.of("category", "AI")));

		vectorStore.add(documents);

		// Test search for AI-related documents
		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("AI machine learning").topK(2).build());

		// Verify that we're getting relevant results
		assertThat(results).isNotEmpty();
		assertThat(results).hasSizeLessThanOrEqualTo(2); // We asked for topK=2

		// The top results should be AI-related documents
		assertThat(results.get(0).getMetadata()).containsEntry("category", "AI");
		assertThat(results.get(0).getText()).containsAnyOf("artificial intelligence", "neural networks");

		// Verify scores are properly ordered (first result should have best score)
		if (results.size() > 1) {
			assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(results.get(1).getScore());
		}

		// Test filtered search - should only return AI documents
		List<Document> filteredResults = vectorStore
			.similaritySearch(SearchRequest.builder().query("AI").topK(5).filterExpression("category == 'AI'").build());

		// Verify all results are AI documents
		assertThat(filteredResults).isNotEmpty();
		assertThat(filteredResults).hasSizeLessThanOrEqualTo(2); // We only have 2 AI
																	// documents

		// All results should have category=AI
		for (Document result : filteredResults) {
			assertThat(result.getMetadata()).containsEntry("category", "AI");
			assertThat(result.getText()).containsAnyOf("artificial intelligence", "neural networks", "deep learning");
		}

		// Test filtered search for DB category
		List<Document> dbFilteredResults = vectorStore.similaritySearch(
				SearchRequest.builder().query("storage").topK(5).filterExpression("category == 'DB'").build());

		// Should only get the database document
		assertThat(dbFilteredResults).hasSize(1);
		assertThat(dbFilteredResults.get(0).getMetadata()).containsEntry("category", "DB");
		assertThat(dbFilteredResults.get(0).getText()).containsIgnoringCase("databases");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public RedisVectorStore vectorStore(EmbeddingModel embeddingModel) {
			return RedisVectorStore
				.builder(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()), embeddingModel)
				.indexName("default-test-index")
				.metadataFields(MetadataField.tag("category"))
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
