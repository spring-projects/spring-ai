/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.s3.autoconfigure;

import java.util.Objects;

import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.s3.S3VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

/**
 * {@link AutoConfiguration Auto-configuration} for S3 Vector Store.
 *
 * @author Matej Nedic
 */
@AutoConfiguration
@ConditionalOnClass({ S3VectorsClient.class, EmbeddingModel.class })
@EnableConfigurationProperties(S3VectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.S3,
		matchIfMissing = true)
public class S3VectorStoreAutoConfiguration {

	private final S3VectorStoreProperties properties;

	S3VectorStoreAutoConfiguration(S3VectorStoreProperties p) {
		Assert.notNull(p.getIndexName(), "Index name cannot be null!");
		Assert.notNull(p.getVectorBucketName(), "Bucket name cannot be null");
		this.properties = p;
	}

	@Bean
	@ConditionalOnMissingBean
	S3VectorStore s3VectorStore(S3VectorsClient s3VectorsClient, EmbeddingModel embeddingModel) {
		S3VectorStore.Builder builder = new S3VectorStore.Builder(s3VectorsClient, embeddingModel);
		builder.indexName(Objects.requireNonNull(this.properties.getIndexName(), "index name cannot be null"))
			.vectorBucketName(
					Objects.requireNonNull(this.properties.getVectorBucketName(), "vector bucket name cannot be null"));
		return builder.build();
	}

}
