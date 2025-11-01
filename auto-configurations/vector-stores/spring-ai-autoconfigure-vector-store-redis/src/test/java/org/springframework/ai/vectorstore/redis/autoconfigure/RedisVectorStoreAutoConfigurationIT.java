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

package org.springframework.ai.vectorstore.redis.autoconfigure;

import java.util.List;
import java.util.Map;

import com.redis.testcontainers.RedisStackContainer;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.test.vectorstore.ObservationTestUtil;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Julien Ruaux
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
class RedisVectorStoreAutoConfigurationIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(DataRedisAutoConfiguration.class, RedisVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.data.redis.url=" + redisContainer.getRedisURI())
		.withPropertyValues("spring.ai.vectorstore.redis.initialize-schema=true")
		.withPropertyValues("spring.ai.vectorstore.redis.index=myIdx")
		.withPropertyValues("spring.ai.vectorstore.redis.prefix=doc:")
		.withPropertyValues("spring.data.redis.client-type=jedis");

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Test
	void addAndSearch() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(this.documents);

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.REDIS,
					VectorStoreObservationContext.Operation.ADD);
			observationRegistry.clear();

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.REDIS,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			ObservationTestUtil.assertObservationRegistry(observationRegistry, VectorStoreProvider.REDIS,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results).isEmpty();
		});
	}

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(RedisVectorStoreProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(RedisVectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(RedisVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(RedisVectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsRedis() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=redis").run(context -> {
			assertThat(context.getBeansOfType(RedisVectorStoreProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(RedisVectorStore.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
