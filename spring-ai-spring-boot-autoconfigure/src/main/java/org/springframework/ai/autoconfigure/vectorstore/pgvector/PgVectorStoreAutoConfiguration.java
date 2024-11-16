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

package org.springframework.ai.autoconfigure.vectorstore.pgvector;

import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link AutoConfiguration Auto-configuration} for PostgreSQL Vector Store.
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Soby Chacko
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({ PgVectorStore.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(PgVectorStoreProperties.class)
public class PgVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy pgVectorStoreBatchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			PgVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		var initializeSchema = properties.isInitializeSchema();

		return new PgVectorStore.Builder(jdbcTemplate, embeddingModel).withSchemaName(properties.getSchemaName())
			.withVectorTableName(properties.getTableName())
			.withVectorTableValidationsEnabled(properties.isSchemaValidation())
			.withDimensions(properties.getDimensions())
			.withDistanceType(properties.getDistanceType())
			.withRemoveExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
			.withIndexType(properties.getIndexType())
			.withInitializeSchema(initializeSchema)
			.withObservationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.withSearchObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.withBatchingStrategy(batchingStrategy)
			.withMaxDocumentBatchSize(properties.getMaxDocumentBatchSize())
			.build();
	}

}
