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

package org.springframework.ai.vectorstore.cassandra.autoconfigure;

import java.time.Duration;
import java.util.Objects;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;
import org.springframework.boot.cassandra.autoconfigure.DriverConfigLoaderBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Cassandra Vector Store.
 *
 * @author Mick Semb Wever
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 1.0.0
 */
@AutoConfiguration(after = CassandraAutoConfiguration.class)
@ConditionalOnClass({ CassandraVectorStore.class, CqlSession.class })
@EnableConfigurationProperties(CassandraVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.CASSANDRA,
		matchIfMissing = true)
public class CassandraVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public CassandraVectorStore vectorStore(EmbeddingModel embeddingModel, CassandraVectorStoreProperties properties,
			CqlSession cqlSession, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		return CassandraVectorStore.builder(embeddingModel)
			.session(cqlSession)
			.keyspace(properties.getKeyspace())
			.table(properties.getTable())
			.contentColumnName(properties.getContentColumnName())
			.embeddingColumnName(properties.getEmbeddingColumnName())
			.indexName(Objects.requireNonNull(properties.getIndexName(), "indexName must be set"))
			.fixedThreadPoolExecutorSize(properties.getFixedThreadPoolExecutorSize())
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

	@Bean
	public DriverConfigLoaderBuilderCustomizer driverConfigLoaderBuilderCustomizer() {
		// this replaces spring-ai-cassandra-*.jar!application.conf
		// as spring-boot autoconfigure will not resolve the default driver configs
		return builder -> builder.startProfile(CassandraVectorStore.DRIVER_PROFILE_UPDATES)
			.withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
			.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(1))
			.withBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE, true)
			.endProfile()
			.startProfile(CassandraVectorStore.DRIVER_PROFILE_SEARCH)
			.withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_ONE")
			.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(10))
			.withBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE, true)
			.endProfile();
	}

}
