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

package org.springframework.ai.vectorstore.cosmosdb.autoconfigure;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import java.util.List;

/**
 * {@link AutoConfiguration Auto-configuration} for CosmosDB Vector Store.
 *
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

		return CosmosDBVectorStore.builder(cosmosAsyncClient, embeddingModel)
			.databaseName(properties.getDatabaseName())
			.containerName(properties.getContainerName())
			.metadataFields(List.of(properties.getMetadataFields()))
			.vectorStoreThroughput(properties.getVectorStoreThroughput())
			.vectorDimensions(properties.getVectorDimensions())
			.partitionKeyPath(properties.getPartitionKeyPath())
			.build();

	}

}
