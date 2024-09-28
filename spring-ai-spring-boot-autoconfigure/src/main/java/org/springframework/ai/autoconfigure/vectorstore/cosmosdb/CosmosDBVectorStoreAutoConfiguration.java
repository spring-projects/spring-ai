package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import com.azure.cosmos.CosmosClientBuilder;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.CassandraVectorStore;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig;
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

@AutoConfiguration
@ConditionalOnClass({ CosmosDBVectorStore.class, EmbeddingModel.class, CosmosAsyncClient.class })
@EnableConfigurationProperties(CosmosDBVectorStoreProperties.class)
public class CosmosDBVectorStoreAutoConfiguration {

	String endpoint;
	String key;

	@Bean
	public CosmosAsyncClient cosmosClient(CosmosDBVectorStoreProperties properties) {
		return new CosmosClientBuilder()
				.endpoint(properties.getEndpoint())
				.key(properties.getKey())
				.gatewayMode()
				.buildAsyncClient();
	}

    @Bean
    @ConditionalOnMissingBean
    public CosmosDBVectorStore cosmosDBVectorStore(ObservationRegistry observationRegistry,
            ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
            CosmosDBVectorStoreProperties properties,
            CosmosAsyncClient cosmosAsyncClient,
            EmbeddingModel embeddingModel) {

		CosmosDBVectorStoreConfig config = new CosmosDBVectorStoreConfig();
		config.setDatabaseName(properties.getDatabaseName());
		config.setContainerName(properties.getContainerName());
        return new CosmosDBVectorStore(observationRegistry,
                customObservationConvention.getIfAvailable(),
                cosmosAsyncClient,
				config,
                embeddingModel);
    }


}
