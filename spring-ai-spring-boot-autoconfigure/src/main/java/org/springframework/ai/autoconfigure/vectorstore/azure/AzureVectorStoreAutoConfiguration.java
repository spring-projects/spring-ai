/*
 * Copyright 2023-2023 the original author or authors.
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

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.AzureVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingClient.class, SearchIndexClient.class })
@EnableConfigurationProperties({ AzureVectorStoreProperties.class })
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.azure", value = { "url", "api-key", "index-name" })
public class AzureVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SearchIndexClient searchIndexClient(AzureVectorStoreProperties properties) {
		return new SearchIndexClientBuilder().endpoint(properties.getUrl())
			.credential(new AzureKeyCredential(properties.getApiKey()))
			.buildClient();
	}

	@Bean
	@ConditionalOnMissingBean
	public VectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingClient embeddingClient,
			AzureVectorStoreProperties properties) {

		var vectorStore = new AzureVectorStore(searchIndexClient, embeddingClient);

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
