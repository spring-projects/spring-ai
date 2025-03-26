/*
 * Copyright 2023 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore.couchbase.autoconfigure;

import com.couchbase.client.java.Cluster;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.CouchbaseSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
@AutoConfiguration(after = CouchbaseAutoConfiguration.class)
@ConditionalOnClass({ CouchbaseSearchVectorStore.class, EmbeddingModel.class, Cluster.class })
@EnableConfigurationProperties(CouchbaseSearchVectorStoreProperties.class)
public class CouchbaseSearchVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CouchbaseSearchVectorStore vectorStore(CouchbaseSearchVectorStoreProperties properties, Cluster cluster,
			EmbeddingModel embeddingModel) {
		var builder = CouchbaseSearchVectorStore.builder(cluster, embeddingModel);

		if (StringUtils.hasText(properties.getIndexName())) {
			builder.vectorIndexName(properties.getIndexName());
		}
		if (StringUtils.hasText(properties.getBucketName())) {
			builder.bucketName(properties.getBucketName());
		}
		if (StringUtils.hasText(properties.getScopeName())) {
			builder.scopeName(properties.getScopeName());
		}
		if (StringUtils.hasText(properties.getCollectionName())) {
			builder.collectionName(properties.getCollectionName());
		}
		if (properties.getDimensions() != null) {
			builder.dimensions(properties.getDimensions());
		}
		if (properties.getSimilarity() != null) {
			builder.similarityFunction(properties.getSimilarity());
		}
		if (properties.getOptimization() != null) {
			builder.indexOptimization(properties.getOptimization());
		}
		return builder.initializeSchema(properties.isInitializeSchema()).build();
	}

}
