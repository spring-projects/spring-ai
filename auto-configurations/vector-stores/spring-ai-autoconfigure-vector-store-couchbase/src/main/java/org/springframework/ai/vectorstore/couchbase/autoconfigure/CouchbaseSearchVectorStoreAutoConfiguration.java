/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.couchbase.autoconfigure;

import com.couchbase.client.java.Cluster;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.couchbase.CouchbaseSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author Laurent Doguin
 * @author Eddú Meléndez
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

		PropertyMapper mapper = PropertyMapper.get();
		mapper.from(properties::getIndexName).whenHasText().to(builder::vectorIndexName);
		mapper.from(properties::getBucketName).whenHasText().to(builder::bucketName);
		mapper.from(properties::getScopeName).whenHasText().to(builder::scopeName);
		mapper.from(properties::getCollectionName).whenHasText().to(builder::collectionName);
		mapper.from(properties::getDimensions).to(builder::dimensions);
		mapper.from(properties::getSimilarity).to(builder::similarityFunction);
		mapper.from(properties::getOptimization).to(builder::indexOptimization);

		return builder.initializeSchema(properties.isInitializeSchema()).build();
	}

}
