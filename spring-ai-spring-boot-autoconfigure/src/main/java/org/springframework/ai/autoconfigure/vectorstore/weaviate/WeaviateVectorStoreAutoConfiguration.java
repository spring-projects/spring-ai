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

package org.springframework.ai.autoconfigure.vectorstore.weaviate;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.WeaviateVectorStore;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig;
import org.springframework.ai.vectorstore.WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingClient.class, WeaviateVectorStore.class })
@EnableConfigurationProperties({ WeaviateVectorStoreProperties.class })
public class WeaviateVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VectorStore vectorStore(EmbeddingClient embeddingClient, WeaviateVectorStoreProperties properties) {

		WeaviateVectorStoreConfig.Builder configBuilder = WeaviateVectorStore.WeaviateVectorStoreConfig.builder()
			.withScheme(properties.getScheme())
			.withApiKey(properties.getApiKey())
			.withHost(properties.getHost())
			.withHeaders(properties.getHeaders())
			.withObjectClass(properties.getObjectClass())
			.withFilterableMetadataFields(properties.getFilterField()
				.entrySet()
				.stream()
				.map(e -> new MetadataField(e.getKey(), e.getValue()))
				.toList())
			.withConsistencyLevel(properties.getConsistencyLevel());

		return new WeaviateVectorStore(configBuilder.build(), embeddingClient);
	}

}
