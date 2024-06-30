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
 * @author Jemin Huh
 * @since 1.0.0
 */
public class OpenSearchVectorStoreOptions {

	/**
	 * The name of the index to store the vectors.
	 */
	private String indexName = "spring-ai-document-index";

	/**
	 * The number of dimensions in the vector.
	 */
	private int dimensions = 1536;

	/**
	 * The similarity function to use. the potential functions for vector fields at
	 * https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces
	 */
	private String similarity = "cosinesimil";

	/**
	 * Indicates whether to use approximate kNN. If true, the approximate kNN method is
	 * used for faster searches and maintains good performance even at large scales.
	 * https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/ If false,
	 * the exact brute-force kNN method is used for precise and highly accurate searches.
	 * https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script/
	 */
	private boolean useApproximateKnn = false;

	private String mappingJson;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(int dims) {
		this.dimensions = dims;
	}

	public String getSimilarity() {
		return similarity;
	}

	public void setSimilarity(String similarity) {
		this.similarity = similarity;
	}

	public boolean isUseApproximateKnn() {
		return this.useApproximateKnn;
	}

	public void setUseApproximateKnn(boolean useApproximateKnn) {
		this.useApproximateKnn = useApproximateKnn;
	}

	public String getMappingJson() {
		return mappingJson;
	}

	public void setMappingJson(String mappingJson) {
		this.mappingJson = mappingJson;
	}

}
