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

package org.springframework.ai.vectorstore.mariadb.autoconfigure;

import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
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
 * @author Diego Dupin
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({ MariaDBVectorStore.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.MARIADB,
		matchIfMissing = true)
public class MariaDbStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy mariaDbStoreBatchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public MariaDBVectorStore mariaDbVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreProperties properties,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		var initializeSchema = properties.isInitializeSchema();

		return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
			.schemaName(properties.getSchemaName())
			.vectorTableName(properties.getTableName())
			.schemaValidation(properties.isSchemaValidation())
			.dimensions(properties.getDimensions())
			.distanceType(properties.getDistanceType())
			.contentFieldName(properties.getContentFieldName())
			.embeddingFieldName(properties.getEmbeddingFieldName())
			.idFieldName(properties.getIdFieldName())
			.metadataFieldName(properties.getMetadataFieldName())
			.removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
			.initializeSchema(initializeSchema)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
			.build();
	}

}
