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

package org.springframework.ai.chat.cache.semantic;

import com.redis.testcontainers.RedisStackContainer;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache;
import org.springframework.ai.vectorstore.redis.cache.semantic.SemanticCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consolidated integration test for Redis-based semantic caching advisor. This test
 * combines the best elements from multiple test classes to provide comprehensive coverage
 * of semantic cache functionality.
 *
 * Tests include: - Basic caching and retrieval - Similarity threshold behavior - TTL
 * (Time-To-Live) support - Cache isolation using namespaces - Redis vector search
 * behavior (KNN vs VECTOR_RANGE) - Automatic caching through advisor pattern
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
@SpringBootTest(classes = SemanticCacheAdvisor2IT.TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class SemanticCacheAdvisor2IT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer("redis/redis-stack:latest")
		.withExposedPorts(6379);

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Autowired
	EmbeddingModel embeddingModel;

	@Autowired
	SemanticCache semanticCache;

	private static final double DEFAULT_DISTANCE_THRESHOLD = 0.4;

	private SemanticCacheAdvisor cacheAdvisor;

	// ApplicationContextRunner for better test isolation and configuration testing
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort());

	@BeforeEach
	void setUp() {
		semanticCache.clear();
		cacheAdvisor = SemanticCacheAdvisor.builder().cache(semanticCache).build();
	}

	@AfterEach
	void tearDown() {
		semanticCache.clear();
	}

	@Test
	void testBasicCachingWithAdvisor() {
		// Test that the advisor automatically caches responses
		String weatherQuestion = "What is the weather like in London today?";

		// First query - should not be cached yet
		ChatResponse londonResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(weatherQuestion)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		assertThat(londonResponse).isNotNull();
		String londonResponseText = londonResponse.getResult().getOutput().getText();

		// Verify the response was automatically cached
		Optional<ChatResponse> cachedResponse = semanticCache.get(weatherQuestion);
		assertThat(cachedResponse).isPresent();
		assertThat(cachedResponse.get().getResult().getOutput().getText()).isEqualTo(londonResponseText);

		// Same query - should use the cache
		ChatResponse secondLondonResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(weatherQuestion)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		assertThat(secondLondonResponse.getResult().getOutput().getText()).isEqualTo(londonResponseText);
	}

	@Test
	void testSimilarityThresholdBehavior() {
		String franceQuestion = "What is the capital of France?";

		// Cache the original response
		ChatResponse franceResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(franceQuestion)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		// Test with similar query using default threshold
		String similarQuestion = "Tell me the capital city of France?";

		ChatResponse similarResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(similarQuestion)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		// With default threshold, similar queries might hit cache
		// We just verify the content is correct
		assertThat(similarResponse.getResult().getOutput().getText()).containsIgnoringCase("Paris");

		// Test with stricter threshold
		JedisPooled jedisPooled = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		SemanticCache strictCache = DefaultSemanticCache.builder()
			.embeddingModel(embeddingModel)
			.jedisClient(jedisPooled)
			.distanceThreshold(0.2) // Very strict
			.build();

		SemanticCacheAdvisor strictAdvisor = SemanticCacheAdvisor.builder().cache(strictCache).build();

		// Cache with strict advisor
		ChatClient.builder(openAiChatModel)
			.build()
			.prompt(franceQuestion)
			.advisors(strictAdvisor)
			.call()
			.chatResponse();

		// Similar query with strict threshold - likely a cache miss
		ChatClient.builder(openAiChatModel)
			.build()
			.prompt(similarQuestion)
			.advisors(strictAdvisor)
			.call()
			.chatResponse();

		// Clean up
		strictCache.clear();
	}

	@Test
	void testTTLSupport() throws InterruptedException {
		String question = "What is the capital of France?";

		ChatResponse initialResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(question)
			.call()
			.chatResponse();

		// Set with TTL
		semanticCache.set(question, initialResponse, Duration.ofSeconds(2));

		// Verify it exists
		Optional<ChatResponse> cached = semanticCache.get(question);
		assertThat(cached).isPresent();

		// Verify TTL is set in Redis
		Optional<JedisPooled> nativeClient = semanticCache.getStore().getNativeClient();
		assertThat(nativeClient).isPresent();
		JedisPooled jedis = nativeClient.get();

		Set<String> keys = jedis.keys("semantic-cache:*");
		assertThat(keys).hasSize(1);
		String key = keys.iterator().next();

		Long ttl = jedis.ttl(key);
		assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(2);

		// Wait for expiration
		Thread.sleep(2500);

		// Verify it's gone
		boolean keyExists = jedis.exists(key);
		assertThat(keyExists).isFalse();

		Optional<ChatResponse> expiredCache = semanticCache.get(question);
		assertThat(expiredCache).isEmpty();
	}

	@Test
	void testCacheIsolationWithNamespaces() {
		String webQuestion = "What are the best programming languages for web development?";

		// Create isolated caches for different users
		JedisPooled jedisPooled1 = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		JedisPooled jedisPooled2 = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		SemanticCache user1Cache = DefaultSemanticCache.builder()
			.embeddingModel(embeddingModel)
			.jedisClient(jedisPooled1)
			.distanceThreshold(DEFAULT_DISTANCE_THRESHOLD)
			.indexName("user1-cache")
			.build();

		SemanticCache user2Cache = DefaultSemanticCache.builder()
			.embeddingModel(embeddingModel)
			.jedisClient(jedisPooled2)
			.distanceThreshold(DEFAULT_DISTANCE_THRESHOLD)
			.indexName("user2-cache")
			.build();

		// Clear both caches
		user1Cache.clear();
		user2Cache.clear();

		SemanticCacheAdvisor user1Advisor = SemanticCacheAdvisor.builder().cache(user1Cache).build();
		SemanticCacheAdvisor user2Advisor = SemanticCacheAdvisor.builder().cache(user2Cache).build();

		// User 1 query
		ChatResponse user1Response = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(webQuestion)
			.advisors(user1Advisor)
			.call()
			.chatResponse();

		String user1ResponseText = user1Response.getResult().getOutput().getText();
		assertThat(user1Cache.get(webQuestion)).isPresent();

		// User 2 query - should not get user1's cached response
		ChatResponse user2Response = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(webQuestion)
			.advisors(user2Advisor)
			.call()
			.chatResponse();

		String user2ResponseText = user2Response.getResult().getOutput().getText();
		assertThat(user2Cache.get(webQuestion)).isPresent();

		// Verify isolation - each user gets their own cached response
		ChatResponse user1SecondResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(webQuestion)
			.advisors(user1Advisor)
			.call()
			.chatResponse();

		assertThat(user1SecondResponse.getResult().getOutput().getText()).isEqualTo(user1ResponseText);

		ChatResponse user2SecondResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(webQuestion)
			.advisors(user2Advisor)
			.call()
			.chatResponse();

		assertThat(user2SecondResponse.getResult().getOutput().getText()).isEqualTo(user2ResponseText);

		// Clean up
		user1Cache.clear();
		user2Cache.clear();
	}

	@Test
	void testMultipleSimilarQueries() {
		// Test with a more lenient threshold for semantic similarity
		JedisPooled jedisPooled = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		SemanticCache testCache = DefaultSemanticCache.builder()
			.embeddingModel(embeddingModel)
			.jedisClient(jedisPooled)
			.distanceThreshold(0.25)
			.build();

		SemanticCacheAdvisor advisor = SemanticCacheAdvisor.builder().cache(testCache).build();

		String originalQuestion = "What is the largest city in Japan?";

		// Cache the original response
		ChatResponse originalResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(originalQuestion)
			.advisors(advisor)
			.call()
			.chatResponse();

		String originalText = originalResponse.getResult().getOutput().getText();
		assertThat(originalText).containsIgnoringCase("Tokyo");

		// Test several semantically similar questions
		String[] similarQuestions = { "Can you tell me the biggest city in Japan?",
				"What is Japan's most populous urban area?", "Which Japanese city has the largest population?" };

		for (String similarQuestion : similarQuestions) {
			ChatResponse response = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(similarQuestion)
				.advisors(advisor)
				.call()
				.chatResponse();

			// Verify the response is about Tokyo
			assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("Tokyo");
		}

		// Test with unrelated query - should not match
		String randomSentence = "Some random sentence.";
		Optional<ChatResponse> randomCheck = testCache.get(randomSentence);
		assertThat(randomCheck).isEmpty();

		// Clean up
		testCache.clear();
	}

	@Test
	void testRedisVectorSearchBehavior() {
		// This test demonstrates the difference between KNN and VECTOR_RANGE search
		String indexName = "test-vector-search-" + System.currentTimeMillis();
		JedisPooled jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		try {
			// Create a vector store for testing
			RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel)
				.indexName(indexName)
				.initializeSchema(true)
				.build();

			vectorStore.afterPropertiesSet();

			// Add a document
			String tokyoText = "Tokyo is the largest city in Japan.";
			Document tokyoDoc = Document.builder().text(tokyoText).build();
			vectorStore.add(Collections.singletonList(tokyoDoc));

			// Wait for index to be ready
			Thread.sleep(1000);

			// Test KNN search - always returns results
			String unrelatedQuery = "How do you make chocolate chip cookies?";
			List<Document> knnResults = vectorStore
				.similaritySearch(SearchRequest.builder().query(unrelatedQuery).topK(1).build());

			assertThat(knnResults).isNotEmpty();
			// KNN always returns results, even if similarity is low

			// Test VECTOR_RANGE search with threshold
			List<Document> rangeResults = vectorStore.searchByRange(unrelatedQuery, 0.2);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			// Clean up
			try {
				jedisClient.ftDropIndex(indexName);
			}
			catch (Exception e) {
				// Ignore cleanup errors
			}
		}
	}

	@Test
	void testBasicCacheOperations() {
		// Test the basic store and check operations
		String prompt = "This is a test prompt.";

		// First call - stores in cache
		ChatResponse firstResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(prompt)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		assertThat(firstResponse).isNotNull();
		String firstResponseText = firstResponse.getResult().getOutput().getText();

		// Second call - should use cache
		ChatResponse secondResponse = ChatClient.builder(openAiChatModel)
			.build()
			.prompt(prompt)
			.advisors(cacheAdvisor)
			.call()
			.chatResponse();

		assertThat(secondResponse).isNotNull();
		String secondResponseText = secondResponse.getResult().getOutput().getText();

		// Should be identical (cache hit)
		assertThat(secondResponseText).isEqualTo(firstResponseText);
	}

	@Test
	void testCacheClear() {
		// Store multiple items
		String[] prompts = { "What is AI?", "What is ML?" };
		String[] firstResponses = new String[prompts.length];

		// Store responses
		for (int i = 0; i < prompts.length; i++) {
			ChatResponse response = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(prompts[i])
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();
			firstResponses[i] = response.getResult().getOutput().getText();
		}

		// Verify items are cached
		for (int i = 0; i < prompts.length; i++) {
			ChatResponse cached = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(prompts[i])
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();
			assertThat(cached.getResult().getOutput().getText()).isEqualTo(firstResponses[i]);
		}

		// Clear cache
		semanticCache.clear();

		// Verify cache is empty
		for (String prompt : prompts) {
			ChatResponse afterClear = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(prompt)
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();
			// After clear, we get a fresh response from the model
			assertThat(afterClear).isNotNull();
		}
	}

	@Test
	void testKnnSearchWithClientSideThreshold() {
		// This test demonstrates client-side threshold filtering with KNN search
		String indexName = "test-knn-threshold-" + System.currentTimeMillis();
		JedisPooled jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		try {
			// Create a vector store for testing
			RedisVectorStore vectorStore = RedisVectorStore.builder(jedisClient, embeddingModel)
				.indexName(indexName)
				.initializeSchema(true)
				.build();

			vectorStore.afterPropertiesSet();

			// Add a document
			String tokyoText = "Tokyo is the largest city in Japan.";
			Document tokyoDoc = Document.builder().text(tokyoText).build();
			vectorStore.add(Collections.singletonList(tokyoDoc));

			// Wait for index to be ready
			Thread.sleep(1000);

			// Test KNN with client-side threshold filtering
			String unrelatedQuery = "How do you make chocolate chip cookies?";
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query(unrelatedQuery)
				.topK(1)
				.similarityThreshold(0.2) // Client-side threshold
				.build());

			// With strict threshold, unrelated query might return empty results
			// This demonstrates the difference between KNN (always returns K results)
			// and client-side filtering (filters by threshold)
			if (!results.isEmpty()) {
				Document doc = results.get(0);
				Double score = doc.getScore();
				// Verify the score meets our threshold
				assertThat(score).isGreaterThanOrEqualTo(0.2);
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			// Clean up
			try {
				jedisClient.ftDropIndex(indexName);
			}
			catch (Exception e) {
				// Ignore cleanup errors
			}
		}
	}

	@Test
	void testDirectCacheVerification() {
		// Test direct cache operations without advisor
		semanticCache.clear();

		// Test with empty cache - should return empty
		String randomQuery = "Some random sentence.";
		Optional<ChatResponse> emptyCheck = semanticCache.get(randomQuery);
		assertThat(emptyCheck).isEmpty();

		// Create a response and cache it directly
		String testPrompt = "What is machine learning?";
		ChatResponse response = ChatClient.builder(openAiChatModel).build().prompt(testPrompt).call().chatResponse();

		// Cache the response directly
		semanticCache.set(testPrompt, response);

		// Verify it's cached
		Optional<ChatResponse> cachedResponse = semanticCache.get(testPrompt);
		assertThat(cachedResponse).isPresent();
		assertThat(cachedResponse.get().getResult().getOutput().getText())
			.isEqualTo(response.getResult().getOutput().getText());

		// Test with similar query - might hit or miss depending on similarity
		String similarQuery = "Explain machine learning to me";
		semanticCache.get(similarQuery);
		// We don't assert presence/absence as it depends on embedding similarity
	}

	@Test
	void testAdvisorWithDifferentConfigurationsUsingContextRunner() {
		// This test demonstrates the value of ApplicationContextRunner for testing
		// different configurations in isolation
		this.contextRunner.run(context -> {
			// Test with default configuration
			SemanticCache defaultCache = context.getBean(SemanticCache.class);
			SemanticCacheAdvisor defaultAdvisor = SemanticCacheAdvisor.builder().cache(defaultCache).build();

			String testQuestion = "What is Spring Boot?";

			// First query with default configuration
			ChatResponse response1 = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(testQuestion)
				.advisors(defaultAdvisor)
				.call()
				.chatResponse();

			assertThat(response1).isNotNull();
			String responseText = response1.getResult().getOutput().getText();

			// Verify it was cached
			Optional<ChatResponse> cached = defaultCache.get(testQuestion);
			assertThat(cached).isPresent();
			assertThat(cached.get().getResult().getOutput().getText()).isEqualTo(responseText);
		});

		// Test with custom configuration (different similarity threshold)
		this.contextRunner.run(context -> {
			JedisPooled jedisPooled = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
			EmbeddingModel embModel = context.getBean(EmbeddingModel.class);

			// Create cache with very strict threshold
			SemanticCache strictCache = DefaultSemanticCache.builder()
				.embeddingModel(embModel)
				.jedisClient(jedisPooled)
				.distanceThreshold(0.1) // Very strict
				.indexName("strict-config-test")
				.build();

			strictCache.clear();
			SemanticCacheAdvisor strictAdvisor = SemanticCacheAdvisor.builder().cache(strictCache).build();

			// Cache a response
			String originalQuery = "What is dependency injection?";
			ChatClient.builder(openAiChatModel)
				.build()
				.prompt(originalQuery)
				.advisors(strictAdvisor)
				.call()
				.chatResponse();

			// Try a similar but not identical query
			String similarQuery = "Explain dependency injection";
			ChatClient.builder(openAiChatModel)
				.build()
				.prompt(similarQuery)
				.advisors(strictAdvisor)
				.call()
				.chatResponse();

			// With strict threshold, these should likely be different responses
			// Clean up
			strictCache.clear();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public SemanticCache semanticCache(EmbeddingModel embeddingModel) {
			JedisPooled jedisPooled = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

			return DefaultSemanticCache.builder()
				.embeddingModel(embeddingModel)
				.jedisClient(jedisPooled)
				.distanceThreshold(DEFAULT_DISTANCE_THRESHOLD)
				.build();
		}

		@Bean(name = "openAiEmbeddingModel")
		public EmbeddingModel embeddingModel() throws Exception {
			// Use the redis/langcache-embed-v1 model
			TransformersEmbeddingModel model = new TransformersEmbeddingModel();
			model.setTokenizerResource("https://huggingface.co/redis/langcache-embed-v1/resolve/main/tokenizer.json");
			model.setModelResource("https://huggingface.co/redis/langcache-embed-v1/resolve/main/onnx/model.onnx");
			model.afterPropertiesSet();
			return model;
		}

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean(name = "openAiChatModel")
		public OpenAiChatModel openAiChatModel(ObservationRegistry observationRegistry) {
			var openAiApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
			var openAiChatOptions = OpenAiChatOptions.builder()
				.model("gpt-3.5-turbo")
				.temperature(0.4)
				.maxTokens(200)
				.build();
			return new OpenAiChatModel(openAiApi, openAiChatOptions, ToolCallingManager.builder().build(),
					new RetryTemplate(), observationRegistry);
		}

	}

}
