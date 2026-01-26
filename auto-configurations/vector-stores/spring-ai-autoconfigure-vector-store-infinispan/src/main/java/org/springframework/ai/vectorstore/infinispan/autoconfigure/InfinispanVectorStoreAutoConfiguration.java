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

package org.springframework.ai.vectorstore.infinispan.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.infinispan.client.hotrod.RemoteCacheManager;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.infinispan.InfinispanVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Infinispan Vector Store.
 *
 * @author Katia Aresti
 */
@AutoConfiguration
@ConditionalOnClass({ InfinispanVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties(InfinispanVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.INFINISPAN,
		matchIfMissing = true)
public class InfinispanVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public InfinispanVectorStore infinispanVectorStore(EmbeddingModel embeddingModel,
			InfinispanVectorStoreProperties properties, RemoteCacheManager infinispanClient,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention) {

		return InfinispanVectorStore.builder(infinispanClient, embeddingModel)
			.createStore(properties.isCreateStore())
			.registerSchema(properties.isRegisterSchema())
			.schemaFileName(properties.getSchemaFileName())
			.storeName(properties.getStoreName())
			.storeConfig(properties.getStoreConfig())
			.observationRegistry(observationRegistry.getIfAvailable())
			.customObservationConvention(customObservationConvention.getIfAvailable())
			.distance(properties.getDistance())
			.similarity(properties.getSimilarity())
			.packageName(properties.getPackageName())
			.springAiItemName(properties.getItemName())
			.metadataItemName(properties.getMetadataItemName())
			.build();
	}

}
