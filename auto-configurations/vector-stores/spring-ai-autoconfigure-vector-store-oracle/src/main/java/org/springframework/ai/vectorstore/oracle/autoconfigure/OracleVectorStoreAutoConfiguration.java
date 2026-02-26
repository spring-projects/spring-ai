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

package org.springframework.ai.vectorstore.oracle.autoconfigure;

import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link AutoConfiguration Auto-configuration} for Oracle Vector Store.
 *
 * @author Loïc Lefèvre
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @author Soby Chacko
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({ OracleVectorStore.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(OracleVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.ORACLE,
		matchIfMissing = true)
public class OracleVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public OracleVectorStore oracleVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OracleVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		return OracleVectorStore.builder(jdbcTemplate, embeddingModel)
			.tableName(properties.getTableName())
			.indexType(properties.getIndexType())
			.distanceType(properties.getDistanceType())
			.dimensions(properties.getDimensions())
			.searchAccuracy(properties.getSearchAccuracy())
			.initializeSchema(properties.isInitializeSchema())
			.removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
			.forcedNormalization(properties.isForcedNormalization())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable())
			.batchingStrategy(batchingStrategy)
			.build();
	}

}
