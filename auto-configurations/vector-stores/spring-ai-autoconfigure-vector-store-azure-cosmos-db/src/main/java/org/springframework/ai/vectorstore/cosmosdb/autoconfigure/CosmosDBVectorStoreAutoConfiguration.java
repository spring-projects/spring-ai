/*
 * Copyright 2023-2025 the original author or authors.
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
import com.azure.identity.DefaultAzureCredentialBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
import org.springframework.ai.vectorstore.cosmosdb.CosmosDBVectorStore.Builder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for CosmosDB Vector Store.
 *
 * @author Theo van Kraay
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass({ CosmosDBVectorStore.class, EmbeddingModel.class, CosmosAsyncClient.class })
@EnableConfigurationProperties(CosmosDBVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.AZURE_COSMOS_DB,
		matchIfMissing = true)
public class CosmosDBVectorStoreAutoConfiguration {

	private static final String agentSuffix = "SpringAI-CDBNoSQL-VectorStore";

	@Bean
	public CosmosAsyncClient cosmosClient(CosmosDBVectorStoreProperties properties) {
		String mode = properties.getConnectionMode();
		if (mode == null) {
			properties.setConnectionMode("gateway");
		}
		else if (!mode.equals("direct") && !mode.equals("gateway")) {
			throw new IllegalArgumentException("Connection mode must be either 'direct' or 'gateway'");
		}

		CosmosClientBuilder builder = new CosmosClientBuilder().endpoint(properties.getEndpoint())
			.userAgentSuffix(agentSuffix);

		if (properties.getKey() == null || properties.getKey().isEmpty()) {
			builder.credential(new DefaultAzureCredentialBuilder().build());
		}
		else {
			builder.key(properties.getKey());
		}

		return ("direct".equals(properties.getConnectionMode()) ? builder.directMode() : builder.gatewayMode())
			.buildAsyncClient();
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public CosmosDBVectorStore cosmosDBVectorStore(ObservationRegistry observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			CosmosDBVectorStoreProperties properties, CosmosAsyncClient cosmosAsyncClient,
			EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {

		Builder builder = CosmosDBVectorStore.builder(cosmosAsyncClient, embeddingModel)
			.metadataFields(properties.getMetadataFieldList())
			.vectorStoreThroughput(properties.getVectorStoreThroughput())
			.vectorDimensions(properties.getVectorDimensions());
		if (properties.getDatabaseName() != null) {
			builder.databaseName(properties.getDatabaseName());
		}
		if (properties.getContainerName() != null) {
			builder.containerName(properties.getContainerName());
		}
		if (properties.getPartitionKeyPath() != null) {
			builder.partitionKeyPath(properties.getPartitionKeyPath());
		}
		return builder.build();
	}

}
