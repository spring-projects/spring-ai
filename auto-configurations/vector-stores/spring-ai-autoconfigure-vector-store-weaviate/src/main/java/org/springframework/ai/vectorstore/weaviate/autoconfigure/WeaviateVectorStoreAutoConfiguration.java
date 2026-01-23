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

package org.springframework.ai.vectorstore.weaviate.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStoreOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Weaviate Vector Store.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Jonghoon Park
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, WeaviateVectorStore.class })
@EnableConfigurationProperties(WeaviateVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.WEAVIATE,
		matchIfMissing = true)
public class WeaviateVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(WeaviateConnectionDetails.class)
	PropertiesWeaviateConnectionDetails weaviateConnectionDetails(WeaviateVectorStoreProperties properties) {
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
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public WeaviateVectorStore weaviateVectorStore(EmbeddingModel embeddingModel, WeaviateClient weaviateClient,
			WeaviateVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {
		return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
			.options(mappingPropertiesToOptions(properties))
			.filterMetadataFields(properties.getFilterField()
				.entrySet()
				.stream()
				.map(e -> new WeaviateVectorStore.MetadataField(e.getKey(), e.getValue()))
				.toList())
			.consistencyLevel(WeaviateVectorStore.ConsistentLevel.valueOf(properties.getConsistencyLevel().name()))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

	WeaviateVectorStoreOptions mappingPropertiesToOptions(WeaviateVectorStoreProperties properties) {
		WeaviateVectorStoreOptions weaviateVectorStoreOptions = new WeaviateVectorStoreOptions();

		PropertyMapper mapper = PropertyMapper.get();
		mapper.from(properties::getContentFieldName).whenHasText().to(weaviateVectorStoreOptions::setContentFieldName);
		mapper.from(properties::getObjectClass).whenHasText().to(weaviateVectorStoreOptions::setObjectClass);
		mapper.from(properties::getMetaFieldPrefix).whenHasText().to(weaviateVectorStoreOptions::setMetaFieldPrefix);

		return weaviateVectorStoreOptions;
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
