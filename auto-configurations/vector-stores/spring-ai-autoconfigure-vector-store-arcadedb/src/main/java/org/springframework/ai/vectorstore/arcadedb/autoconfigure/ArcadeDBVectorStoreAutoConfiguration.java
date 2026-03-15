/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb.autoconfigure;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.arcadedb.ArcadeDBDistanceType;
import org.springframework.ai.vectorstore.arcadedb.ArcadeDBVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for ArcadeDB VectorStore.
 *
 * <p>
 * Creates an embedded {@link Database} bean and an
 * {@link ArcadeDBVectorStore} bean.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ArcadeDBVectorStore.class)
@EnableConfigurationProperties(ArcadeDBVectorStoreProperties.class)
public class ArcadeDBVectorStoreAutoConfiguration {

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ArcadeDBVectorStoreProperties.CONFIG_PREFIX,
			name = "database-path")
	public Database arcadeDatabase(ArcadeDBVectorStoreProperties properties) {
		DatabaseFactory factory = new DatabaseFactory(
				properties.getDatabasePath());
		return factory.exists() ? factory.open() : factory.create();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ArcadeDBVectorStoreProperties.CONFIG_PREFIX,
			name = "database-path")
	public ArcadeDBVectorStore arcadeDBVectorStore(Database database,
			EmbeddingModel embeddingModel,
			ArcadeDBVectorStoreProperties properties) {
		ArcadeDBVectorStore store = ArcadeDBVectorStore
				.builder(embeddingModel)
				.database(database)
				.typeName(properties.getTypeName())
				.embeddingDimension(properties.getEmbeddingDimension())
				.distanceType(ArcadeDBDistanceType
						.valueOf(properties.getDistanceType()))
				.initializeSchema(properties.isInitializeSchema())
				.m(properties.getM())
				.ef(properties.getEf())
				.efConstruction(properties.getEfConstruction())
				.metadataPrefix(properties.getMetadataPrefix())
				.build();
		store.afterPropertiesSet();
		return store;
	}

}
