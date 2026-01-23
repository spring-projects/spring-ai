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

import io.micrometer.observation.ObservationRegistry;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

/**
 * {@link AutoConfiguration Auto-configuration} for Redis Vector Store.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Brian Sam-Bodden
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass({ JedisPooled.class, JedisConnectionFactory.class, RedisVectorStore.class, EmbeddingModel.class })
@ConditionalOnBean(JedisConnectionFactory.class)
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.REDIS,
		matchIfMissing = true)
public class RedisVectorStoreAutoConfiguration {

	/**
	 * Creates a default batching strategy for the vector store.
	 * @return a token count batching strategy
	 */
	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	/**
	 * Creates a Redis vector store.
	 * @param embeddingModel the embedding model
	 * @param properties the Redis vector store properties
	 * @param jedisConnectionFactory the Jedis connection factory
	 * @param observationRegistry the observation registry
	 * @param convention the custom observation convention
	 * @param batchingStrategy the batching strategy
	 * @return the configured Redis vector store
	 */
	@Bean
	@ConditionalOnMissingBean
	public RedisVectorStore vectorStore(final EmbeddingModel embeddingModel,
			final RedisVectorStoreProperties properties, final JedisConnectionFactory jedisConnectionFactory,
			final ObjectProvider<ObservationRegistry> observationRegistry,
			final ObjectProvider<VectorStoreObservationConvention> convention,
			final BatchingStrategy batchingStrategy) {

		JedisPooled jedisPooled = jedisPooled(jedisConnectionFactory);
		RedisVectorStore.Builder builder = RedisVectorStore.builder(jedisPooled, embeddingModel)
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(convention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.indexName(properties.getIndexName())
			.prefix(properties.getPrefix());

		// Configure HNSW parameters if available
		hnswConfiguration(builder, properties);

		return builder.build();
	}

	/**
	 * Configures the HNSW-related parameters on the builder.
	 * @param builder the Redis vector store builder
	 * @param properties the Redis vector store properties
	 */
	private void hnswConfiguration(final RedisVectorStore.Builder builder,
			final RedisVectorStoreProperties properties) {
		builder.hnswM(properties.getHnsw().getM())
			.hnswEfConstruction(properties.getHnsw().getEfConstruction())
			.hnswEfRuntime(properties.getHnsw().getEfRuntime());
	}

	private JedisPooled jedisPooled(final JedisConnectionFactory jedisConnectionFactory) {

		String host = jedisConnectionFactory.getHostName();
		int port = jedisConnectionFactory.getPort();

		JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
			.ssl(jedisConnectionFactory.isUseSsl())
			.clientName(jedisConnectionFactory.getClientName())
			.timeoutMillis(jedisConnectionFactory.getTimeout())
			.password(jedisConnectionFactory.getPassword())
			.build();

		return new JedisPooled(new HostAndPort(host, port), clientConfig);
	}

}
