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
package org.springframework.ai.vectorstore.redis.cache.semantic.autoconfigure;

import com.redis.testcontainers.RedisStackContainer;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.cache.semantic.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache;
import org.springframework.ai.chat.cache.semantic.SemanticCache;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RedisSemanticCacheAutoConfiguration}.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class RedisSemanticCacheAutoConfigurationIT {

	private static final Logger logger = LoggerFactory.getLogger(RedisSemanticCacheAutoConfigurationIT.class);

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))
		.withExposedPorts(6379);

	@BeforeAll
	static void setup() {
		logger.debug("Redis container running on host: {} and port: {}", redisContainer.getHost(),
				redisContainer.getFirstMappedPort());
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(RedisSemanticCacheAutoConfiguration.class, DataRedisAutoConfiguration.class))
		.withUserConfiguration(TestConfig.class)
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort(),
				// Pass the same Redis connection properties to our semantic cache
				// properties
				"spring.ai.vectorstore.redis.semantic-cache.host=" + redisContainer.getHost(),
				"spring.ai.vectorstore.redis.semantic-cache.port=" + redisContainer.getFirstMappedPort());

	@Test
	void autoConfigurationRegistersExpectedBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(SemanticCache.class);
			assertThat(context).hasSingleBean(DefaultSemanticCache.class);
			assertThat(context).hasSingleBean(SemanticCacheAdvisor.class);

			// Verify the advisor is correctly implementing the right interfaces
			SemanticCacheAdvisor advisor = context.getBean(SemanticCacheAdvisor.class);

			// Test using instanceof
			assertThat(advisor).isInstanceOf(Advisor.class);
			// assertThat(advisor).isInstanceOf(CallAroundAdvisor.class);
			// assertThat(advisor).isInstanceOf(StreamAroundAdvisor.class);

			// Test using class equality instead of direct instanceof
			assertThat(CallAdvisor.class.isAssignableFrom(advisor.getClass())).isTrue();
			assertThat(StreamAdvisor.class.isAssignableFrom(advisor.getClass())).isTrue();
		});
	}

	@Test
	void customPropertiesAreApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.redis.semantic-cache.index-name=custom-index",
					"spring.ai.vectorstore.redis.semantic-cache.prefix=custom-prefix:",
					"spring.ai.vectorstore.redis.semantic-cache.similarity-threshold=0.85")
			.run(context -> {
				SemanticCache semanticCache = context.getBean(SemanticCache.class);
				assertThat(semanticCache).isNotNull();
			});
	}

	@Test
	void autoConfigurationDisabledWhenDisabledPropertyIsSet() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.redis.semantic-cache.enabled=false")
			.run(context -> {
				assertThat(context.getBeansOfType(RedisSemanticCacheProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(SemanticCache.class)).isEmpty();
				assertThat(context.getBeansOfType(DefaultSemanticCache.class)).isEmpty();
				assertThat(context.getBeansOfType(SemanticCacheAdvisor.class)).isEmpty();
			});
	}

	@Configuration
	static class TestConfig {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			// Get API key from environment variable
			String apiKey = System.getenv("OPENAI_API_KEY");
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		}

	}

}