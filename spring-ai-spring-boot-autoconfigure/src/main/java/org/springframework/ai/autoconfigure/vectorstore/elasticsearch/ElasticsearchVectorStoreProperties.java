/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.elasticsearch;

import org.springframework.ai.autoconfigure.CommonVectorStoreProperties;
import org.springframework.ai.vectorstore.SimilarityFunction;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Eddú Meléndez
 * @author Wei Jiang
 * @author Josh Long
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.vectorstore.elasticsearch")
public class ElasticsearchVectorStoreProperties extends CommonVectorStoreProperties {

	/**
	 * The name of the index to store the vectors.
	 */
	private String indexName;

	/**
	 * The number of dimensions in the vector.
	 */
	private Integer dimensions;

	/**
	 * The similarity function to use.
	 */
	private SimilarityFunction similarity;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public Integer getDimensions() {
		return dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public SimilarityFunction getSimilarity() {
		return similarity;
	}

	public void setSimilarity(SimilarityFunction similarity) {
		this.similarity = similarity;
	}

}
