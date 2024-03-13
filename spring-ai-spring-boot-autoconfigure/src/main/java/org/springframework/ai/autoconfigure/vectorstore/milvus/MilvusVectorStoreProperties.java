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
package org.springframework.ai.autoconfigure.vectorstore.milvus;

import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(MilvusVectorStoreProperties.CONFIG_PREFIX)
public class MilvusVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.milvus";

	/**
	 * The name of the Milvus database to connect to.
	 */
	private String databaseName = MilvusVectorStore.DEFAULT_DATABASE_NAME;

	/**
	 * Milvus collection name to store the vectors.
	 */
	private String collectionName = MilvusVectorStore.DEFAULT_COLLECTION_NAME;

	/**
	 * The dimension of the vectors to be stored in the Milvus collection.
	 */
	private int embeddingDimension = MilvusVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

	/**
	 * The type of the index to be created for the Milvus collection.
	 */
	private MilvusIndexType indexType = MilvusIndexType.IVF_FLAT;

	/**
	 * The metric type to be used for the Milvus collection.
	 */
	private MilvusMetricType metricType = MilvusMetricType.COSINE;

	/**
	 * The index parameters to be used for the Milvus collection.
	 */
	private String indexParameters = "{\"nlist\":1024}";

	public enum MilvusMetricType {

		/**
		 * Invalid metric type
		 */
		INVALID,
		/**
		 * Euclidean distance
		 */
		L2,
		/**
		 * Inner product
		 */
		IP,
		/**
		 * Cosine distance
		 */
		COSINE,
		/**
		 * Hamming distance
		 */
		HAMMING,
		/**
		 * Jaccard distance
		 */
		JACCARD;

	}

	public enum MilvusIndexType {

		INVALID, FLAT, IVF_FLAT, IVF_SQ8, IVF_PQ, HNSW, DISKANN, AUTOINDEX, SCANN, GPU_IVF_FLAT, GPU_IVF_PQ, BIN_FLAT,
		BIN_IVF_FLAT, TRIE, STL_SORT;

	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		Assert.hasText(databaseName, "Database name should not be empty.");
		this.databaseName = databaseName;
	}

	public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		Assert.hasText(collectionName, "Collection name should not be empty.");
		this.collectionName = collectionName;
	}

	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public void setEmbeddingDimension(int embeddingDimension) {
		Assert.isTrue(embeddingDimension > 0, "Embedding dimension should be a positive value.");
		this.embeddingDimension = embeddingDimension;
	}

	public MilvusIndexType getIndexType() {
		return indexType;
	}

	public void setIndexType(MilvusIndexType indexType) {
		Assert.notNull(indexType, "Index type can not be null");
		this.indexType = indexType;
	}

	public MilvusMetricType getMetricType() {
		return metricType;
	}

	public void setMetricType(MilvusMetricType metricType) {
		Assert.notNull(metricType, "MetricType can not be null");
		this.metricType = metricType;
	}

	public String getIndexParameters() {
		return indexParameters;
	}

	public void setIndexParameters(String indexParameters) {
		Assert.notNull(indexParameters, "indexParameters can not be null");
		this.indexParameters = indexParameters;
	}

}
