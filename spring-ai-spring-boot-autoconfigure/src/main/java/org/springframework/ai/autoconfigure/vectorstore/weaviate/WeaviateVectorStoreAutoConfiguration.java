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

package org.springframework.ai.autoconfigure.vectorstore.weaviate;

import io.micrometer.observation.ObservationRegistry;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.WeaviateVectorStore;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, WeaviateVectorStore.class })
@EnableConfigurationProperties({ WeaviateVectorStoreProperties.class })
public class WeaviateVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(WeaviateConnectionDetails.class)
	public PropertiesWeaviateConnectionDetails weaviateConnectionDetails(WeaviateVectorStoreProperties properties) {
		return new PropertiesWeaviateConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public WeaviateClient weaviateClient(WeaviateVectorStoreProperties properties,
			WeaviateConnectionDetails connectionDetails) {
		try {
			return WeaviateAuthClient.apiKey(
					new Config(properties.getScheme(), connectionDetails.getHost(), properties.getHeaders()),
					properties.getApiKey());
		}
		catch (AuthException e) {
			throw new IllegalArgumentException("WeaviateClient could not be created.", e);
		}
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public WeaviateVectorStore vectorStore(EmbeddingModel embeddingModel, WeaviateClient weaviateClient,
			WeaviateVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		WeaviateVectorStoreConfig.Builder configBuilder = WeaviateVectorStore.WeaviateVectorStoreConfig.builder()
			.withObjectClass(properties.getObjectClass())
			.withFilterableMetadataFields(properties.getFilterField()
				.entrySet()
				.stream()
				.map(e -> new MetadataField(e.getKey(), e.getValue()))
				.toList())
			.withConsistencyLevel(properties.getConsistencyLevel());

		return new WeaviateVectorStore(configBuilder.build(), embeddingModel, weaviateClient,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null), batchingStrategy);
	}

	static class PropertiesWeaviateConnectionDetails implements WeaviateConnectionDetails {

		private final WeaviateVectorStoreProperties properties;

		PropertiesWeaviateConnectionDetails(WeaviateVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getHost() {
			return this.properties.getHost();
		}

	}

}
