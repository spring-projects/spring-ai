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

package org.springframework.ai.vectorstore.elasticsearch.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Elasticsearch Vector Store.
 *
 * @author Eddú Meléndez
 * @author Wei Jiang
 * @author Josh Long
 * @author Jonghoon Park
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.vectorstore.elasticsearch")
public class ElasticsearchVectorStoreProperties extends CommonVectorStoreProperties {

	/**
	 * The name of the index to store the vectors.
	 */
	private @Nullable String indexName;

	/**
	 * The number of dimensions in the vector.
	 */
	private @Nullable Integer dimensions;

	/**
	 * The similarity function to use.
	 */
	private @Nullable SimilarityFunction similarity;

	/**
	 * The name of the vector field to search against
	 */
	private String embeddingFieldName = "embedding";

	public @Nullable String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public @Nullable SimilarityFunction getSimilarity() {
		return this.similarity;
	}

	public void setSimilarity(@Nullable SimilarityFunction similarity) {
		this.similarity = similarity;
	}

	public String getEmbeddingFieldName() {
		return this.embeddingFieldName;
	}

	public void setEmbeddingFieldName(String embeddingFieldName) {
		this.embeddingFieldName = embeddingFieldName;
	}

}
