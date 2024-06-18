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
package org.springframework.ai.vectorstore;

/**
 * Provided Elasticsearch vector option configuration.
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class ElasticsearchVectorStoreOptions {

	/**
	 * The name of the index to store the vectors.
	 */
	private String indexName = "spring-ai-document-index";

	/**
	 * The number of dimensions in the vector.
	 */
	private int dimensions = 1536;

	/**
	 * The similarity function to use.
	 */
	private SimilarityFunction similarity = SimilarityFunction.cosine;

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getDimensions() {
		return dimensions;
	}

	public void setDimensions(int dims) {
		this.dimensions = dims;
	}

	public SimilarityFunction getSimilarity() {
		return similarity;
	}

	public void setSimilarity(SimilarityFunction similarity) {
		this.similarity = similarity;
	}

}
