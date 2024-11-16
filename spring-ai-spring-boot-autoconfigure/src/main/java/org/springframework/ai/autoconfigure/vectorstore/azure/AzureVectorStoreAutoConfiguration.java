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

package org.springframework.ai.autoconfigure.vectorstore.azure;

import java.util.List;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.ClientOptions;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure Vector Store.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, SearchIndexClient.class, AzureVectorStore.class })
@EnableConfigurationProperties({ AzureVectorStoreProperties.class })
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.azure", value = { "url", "api-key", "index-name" })
public class AzureVectorStoreAutoConfiguration {

	private final static String APPLICATION_ID = "spring-ai";

	@Bean
	@ConditionalOnMissingBean
	public SearchIndexClient searchIndexClient(AzureVectorStoreProperties properties) {
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setApplicationId(APPLICATION_ID);
		return new SearchIndexClientBuilder().endpoint(properties.getUrl())
			.credential(new AzureKeyCredential(properties.getApiKey()))
			.clientOptions(clientOptions)
			.buildClient();
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public AzureVectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel,
			AzureVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		var vectorStore = new AzureVectorStore(searchIndexClient, embeddingModel, properties.isInitializeSchema(),
				List.of(), observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null), batchingStrategy);

		vectorStore.setIndexName(properties.getIndexName());

		if (properties.getDefaultTopK() >= 0) {
			vectorStore.setDefaultTopK(properties.getDefaultTopK());
		}

		if (properties.getDefaultSimilarityThreshold() >= 0.0) {
			vectorStore.setDefaultSimilarityThreshold(properties.getDefaultSimilarityThreshold());
		}

		return vectorStore;
	}

}
