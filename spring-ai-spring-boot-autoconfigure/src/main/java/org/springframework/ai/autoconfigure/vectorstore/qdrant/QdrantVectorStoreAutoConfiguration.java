/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.qdrant;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore.QdrantVectorStoreConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Anush Shetty
 * @since 0.8.1
 */
@AutoConfiguration
@ConditionalOnClass({ QdrantVectorStore.class, EmbeddingClient.class })
@EnableConfigurationProperties(QdrantVectorStoreProperties.class)
public class QdrantVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VectorStore vectorStore(EmbeddingClient embeddingClient, QdrantVectorStoreProperties properties) {

		var config = QdrantVectorStoreConfig.builder()
			.withCollectionName(properties.getCollectionName())
			.withHost(properties.getHost())
			.withPort(properties.getPort())
			.withTls(properties.isUseTls())
			.withApiKey(properties.getApiKey())
			.build();

		return new QdrantVectorStore(config, embeddingClient);
	}

}
