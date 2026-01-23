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

package org.springframework.ai.vectorstore.qdrant.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Qdrant Vector Store.
 *
 * @author Anush Shetty
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 0.8.1
 */
@AutoConfiguration
@ConditionalOnClass({ QdrantVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties(QdrantVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.QDRANT,
		matchIfMissing = true)
public class QdrantVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(QdrantConnectionDetails.class)
	PropertiesQdrantConnectionDetails qdrantConnectionDetails(QdrantVectorStoreProperties properties) {
		return new PropertiesQdrantConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public QdrantClient qdrantClient(QdrantVectorStoreProperties properties,
			QdrantConnectionDetails connectionDetails) {
		QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(connectionDetails.getHost(),
				connectionDetails.getPort(), properties.isUseTls());

		if (connectionDetails.getApiKey() != null) {
			grpcClientBuilder.withApiKey(connectionDetails.getApiKey());
		}
		return new QdrantClient(grpcClientBuilder.build());
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public QdrantVectorStore qdrantVectorStore(EmbeddingModel embeddingModel, QdrantVectorStoreProperties properties,
			QdrantClient qdrantClient, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {
		return QdrantVectorStore.builder(qdrantClient, embeddingModel)
			.collectionName(properties.getCollectionName())
			.contentFieldName(properties.getContentFieldName())
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

	static class PropertiesQdrantConnectionDetails implements QdrantConnectionDetails {

		private final QdrantVectorStoreProperties properties;

		PropertiesQdrantConnectionDetails(QdrantVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getHost() {
			return this.properties.getHost();
		}

		@Override
		public int getPort() {
			return this.properties.getPort();
		}

		@Override
		public String getApiKey() {
			return this.properties.getApiKey();
		}

	}

}
