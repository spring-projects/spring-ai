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

package org.springframework.ai.vectorstore.typesense.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.typesense.TypesenseVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Typesense Vector Store.
 *
 * @author Pablo Sanchidrian Herrera
 * @author Eddú Meléndez
 * @author Soby Chacko
 */
@AutoConfiguration
@ConditionalOnClass({ TypesenseVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties({ TypesenseServiceClientProperties.class, TypesenseVectorStoreProperties.class })
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.TYPESENSE,
		matchIfMissing = true)
public class TypesenseVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(TypesenseConnectionDetails.class)
	TypesenseVectorStoreAutoConfiguration.PropertiesTypesenseConnectionDetails typesenseServiceClientConnectionDetails(
			TypesenseServiceClientProperties properties) {
		return new TypesenseVectorStoreAutoConfiguration.PropertiesTypesenseConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public TypesenseVectorStore typesenseVectorStore(Client typesenseClient, EmbeddingModel embeddingModel,
			TypesenseVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		return TypesenseVectorStore.builder(typesenseClient, embeddingModel)
			.collectionName(properties.getCollectionName())
			.embeddingDimension(properties.getEmbeddingDimension())
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public Client typesenseClient(TypesenseConnectionDetails connectionDetails) {
		List<Node> nodes = new ArrayList<>();
		nodes.add(new Node(connectionDetails.getProtocol(), connectionDetails.getHost(),
				String.valueOf(connectionDetails.getPort())));

		Configuration configuration = new Configuration(nodes, Duration.ofSeconds(5), connectionDetails.getApiKey());
		return new Client(configuration);
	}

	static class PropertiesTypesenseConnectionDetails implements TypesenseConnectionDetails {

		private final TypesenseServiceClientProperties properties;

		PropertiesTypesenseConnectionDetails(TypesenseServiceClientProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getProtocol() {
			return this.properties.getProtocol();
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
