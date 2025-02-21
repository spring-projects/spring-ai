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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.cache.semantic.SemanticCacheAdvisorIT.TestApplication;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache;
import org.springframework.ai.vectorstore.redis.cache.semantic.SemanticCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the Redis-based advisor that provides semantic caching capabilities for chat
 * responses
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class SemanticCacheAdvisorIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.data.redis.url=" + redisContainer.getRedisURI());

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Autowired
	SemanticCache semanticCache;

	@AfterEach
	void tearDown() {
		semanticCache.clear();
	}

	@Test
	void semanticCacheTest() {
		this.contextRunner.run(context -> {
			String question = "What is the capital of France?";
			String expectedResponse = "Paris is the capital of France.";

			// First, simulate a cached response
			semanticCache.set(question, createMockResponse(expectedResponse));

			// Create advisor
			SemanticCacheAdvisor cacheAdvisor = SemanticCacheAdvisor.builder().cache(semanticCache).build();

			// Test with a semantically similar question
			String similarQuestion = "Tell me which city is France's capital?";
			ChatResponse chatResponse = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(similarQuestion)
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();

			assertThat(chatResponse).isNotNull();
			String response = chatResponse.getResult().getOutput().getText();
			assertThat(response).containsIgnoringCase("Paris");

			// Test cache miss with a different question
			String differentQuestion = "What is the population of Tokyo?";
			ChatResponse newResponse = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(differentQuestion)
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();

			assertThat(newResponse).isNotNull();
			String newResponseText = newResponse.getResult().getOutput().getText();
			assertThat(newResponseText).doesNotContain(expectedResponse);

			// Verify the new response was cached
			ChatResponse cachedNewResponse = semanticCache.get(differentQuestion).orElseThrow();
			assertThat(cachedNewResponse.getResult().getOutput().getText())
				.isEqualTo(newResponse.getResult().getOutput().getText());
		});
	}

	@Test
	void semanticCacheTTLTest() throws InterruptedException {
		this.contextRunner.run(context -> {
			String question = "What is the capital of France?";
			String expectedResponse = "Paris is the capital of France.";

			// Set with short TTL
			semanticCache.set(question, createMockResponse(expectedResponse), Duration.ofSeconds(2));

			// Create advisor
			SemanticCacheAdvisor cacheAdvisor = SemanticCacheAdvisor.builder().cache(semanticCache).build();

			// Verify key exists
			Optional<JedisPooled> nativeClient = semanticCache.getStore().getNativeClient();
			assertThat(nativeClient).isPresent();
			JedisPooled jedis = nativeClient.get();

			Set<String> keys = jedis.keys("semantic-cache:*");
			assertThat(keys).hasSize(1);
			String key = keys.iterator().next();

			// Verify TTL is set
			Long ttl = jedis.ttl(key);
			assertThat(ttl).isGreaterThan(0);
			assertThat(ttl).isLessThanOrEqualTo(2);

			// Test cache hit before expiry
			String similarQuestion = "Tell me which city is France's capital?";
			ChatResponse chatResponse = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(similarQuestion)
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();

			assertThat(chatResponse).isNotNull();
			assertThat(chatResponse.getResult().getOutput().getText()).containsIgnoringCase("Paris");

			// Wait for TTL to expire
			Thread.sleep(2100);

			// Verify key is gone
			assertThat(jedis.exists(key)).isFalse();

			// Should get a cache miss and new response
			ChatResponse newResponse = ChatClient.builder(openAiChatModel)
				.build()
				.prompt(similarQuestion)
				.advisors(cacheAdvisor)
				.call()
				.chatResponse();

			assertThat(newResponse).isNotNull();
			assertThat(newResponse.getResult().getOutput().getText()).containsIgnoringCase("Paris");
			// Original cached response should be gone, this should be a fresh response
		});
	}

	private ChatResponse createMockResponse(String text) {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text)))).build();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public SemanticCache semanticCache(EmbeddingModel embeddingModel,
				JedisConnectionFactory jedisConnectionFactory) {
			JedisPooled jedisPooled = new JedisPooled(Objects.requireNonNull(jedisConnectionFactory.getPoolConfig()),
					jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort());

			return DefaultSemanticCache.builder().embeddingModel(embeddingModel).jedisClient(jedisPooled).build();
		}

		@Bean(name = "openAiEmbeddingModel")
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

		@Bean(name = "openAiChatModel")
		public OpenAiChatModel openAiChatModel() {
			var openAiApi = new OpenAiApi(System.getenv("OPENAI_API_KEY"));
			var openAiChatOptions = OpenAiChatOptions.builder()
				.model("gpt-3.5-turbo")
				.temperature(0.4)
				.maxTokens(200)
				.build();
			return new OpenAiChatModel(openAiApi, openAiChatOptions);
		}

	}

}
