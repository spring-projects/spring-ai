/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import com.azure.cosmos.CosmosClientBuilder;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.CosmosDBVectorStore;
import org.springframework.ai.vectorstore.CosmosDBVectorStoreConfig;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import com.azure.cosmos.CosmosAsyncClient;
import io.micrometer.observation.ObservationRegistry;

/**
 * @author Theo van Kraay
 * @author Soby Chacko
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ CosmosDBVectorStore.class, EmbeddingModel.class, CosmosAsyncClient.class })
@EnableConfigurationProperties(CosmosDBVectorStoreProperties.class)
public class CosmosDBVectorStoreAutoConfiguration {

	String endpoint;

	String key;

	@Bean
	public CosmosAsyncClient cosmosClient(CosmosDBVectorStoreProperties properties) {
		return new CosmosClientBuilder().endpoint(properties.getEndpoint())
			.userAgentSuffix("SpringAI-CDBNoSQL-VectorStore")
			.key(properties.getKey())
			.gatewayMode()
			.buildAsyncClient();
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public CosmosDBVectorStore cosmosDBVectorStore(ObservationRegistry observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			CosmosDBVectorStoreProperties properties, CosmosAsyncClient cosmosAsyncClient,
			EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {

		CosmosDBVectorStoreConfig config = new CosmosDBVectorStoreConfig();
		config.setDatabaseName(properties.getDatabaseName());
		config.setContainerName(properties.getContainerName());
		config.setMetadataFields(properties.getMetadataFields());
		config.setVectorStoreThoughput(properties.getVectorStoreThoughput());
		config.setVectorDimensions(properties.getVectorDimensions());
		return new CosmosDBVectorStore(observationRegistry, customObservationConvention.getIfAvailable(),
				cosmosAsyncClient, config, embeddingModel, batchingStrategy);
	}

}
