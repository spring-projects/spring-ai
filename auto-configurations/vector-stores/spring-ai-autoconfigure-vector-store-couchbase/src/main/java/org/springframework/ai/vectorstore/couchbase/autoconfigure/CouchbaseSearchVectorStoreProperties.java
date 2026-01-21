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

import org.springframework.ai.vectorstore.couchbase.CouchbaseIndexOptimization;
import org.springframework.ai.vectorstore.couchbase.CouchbaseSimilarityFunction;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = CouchbaseSearchVectorStoreProperties.CONFIG_PREFIX)
public class CouchbaseSearchVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.couchbase";

	/**
	 * The name of the index to store the vectors.
	 */
	private String indexName;

	/**
	 * The name of the Couchbase collection to store the Documents.
	 */
	private String collectionName;

	/**
	 * The name of the Couchbase scope, parent of the collection. Search queries will be
	 * executed in the scope context.
	 */
	private String scopeName;

	/**
	 * The name of the Couchbase Bucket, parent of the scope.
	 */
	private String bucketName;

	/**
	 * The total number of elements in the vector embedding array, up to 2048 elements.
	 * Arrays can be an array of arrays.
	 */
	private Integer dimensions;

	/**
	 * The method to calculate the similarity between the vector embedding in a Vector
	 * Search index and the vector embedding in a Vector Search query.
	 */
	private CouchbaseSimilarityFunction similarity;

	/**
	 * Choose whether the Search Service should prioritize recall or latency when
	 * returning similar vectors in search results.
	 */
	private CouchbaseIndexOptimization optimization;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getScopeName() {
		return this.scopeName;
	}

	public void setScopeName(String scopeName) {
		this.scopeName = scopeName;
	}

	public String getBucketName() {
		return this.bucketName;
	}

	public void setBucketName(String bucketName) {
		this.bucketName = bucketName;
	}

	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public CouchbaseSimilarityFunction getSimilarity() {
		return this.similarity;
	}

	public void setSimilarity(CouchbaseSimilarityFunction similarity) {
		this.similarity = similarity;
	}

	public CouchbaseIndexOptimization getOptimization() {
		return this.optimization;
	}

	public void setOptimization(CouchbaseIndexOptimization optimization) {
		this.optimization = optimization;
	}

}
