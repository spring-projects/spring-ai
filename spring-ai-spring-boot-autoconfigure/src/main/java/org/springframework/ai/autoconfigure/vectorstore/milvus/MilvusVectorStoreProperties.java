/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.milvus;

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;

import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import static org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreProperties.CONFIG_PREFIX;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(CONFIG_PREFIX)
public class MilvusVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.milvus";

	/**
	 * The database name
	 */
	private String databaseName = MilvusVectorStore.DEFAULT_DATABASE_NAME;

	private String collectionName = MilvusVectorStore.DEFAULT_COLLECTION_NAME;

	private int embeddingDimension = MilvusVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

	private IndexType indexType = IndexType.IVF_FLAT;

	private MetricType metricType = MetricType.COSINE;

	private String indexParameters = "{\"nlist\":1024}";

	public static String getConfigPrefix() {
		return CONFIG_PREFIX;
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

	public IndexType getIndexType() {
		return indexType;
	}

	public void setIndexType(IndexType indexType) {
		Assert.notNull(indexType, "Index type can not be null");
		this.indexType = indexType;
	}

	public MetricType getMetricType() {
		return metricType;
	}

	public void setMetricType(MetricType metricType) {
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
