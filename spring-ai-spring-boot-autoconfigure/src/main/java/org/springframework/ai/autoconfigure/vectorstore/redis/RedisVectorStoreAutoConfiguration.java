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

package org.springframework.ai.autoconfigure.vectorstore.redis;

import io.micrometer.observation.ObservationRegistry;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

/**
 * {@link AutoConfiguration Auto-configuration} for Redis Vector Store.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Jihoon Kim
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({ JedisPooled.class, JedisConnectionFactory.class, RedisVectorStore.class, EmbeddingModel.class })
@ConditionalOnBean(JedisConnectionFactory.class)
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
public class RedisVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public RedisVectorStore vectorStore(EmbeddingModel embeddingModel, RedisVectorStoreProperties properties,
			JedisConnectionFactory jedisConnectionFactory, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		JedisPooled jedisPooled = this.jedisPooled(jedisConnectionFactory);
		return RedisVectorStore.builder(jedisPooled, embeddingModel)
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.indexName(properties.getIndex())
			.prefix(properties.getPrefix())
			.build();
	}

	private JedisPooled jedisPooled(JedisConnectionFactory jedisConnectionFactory) {

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
