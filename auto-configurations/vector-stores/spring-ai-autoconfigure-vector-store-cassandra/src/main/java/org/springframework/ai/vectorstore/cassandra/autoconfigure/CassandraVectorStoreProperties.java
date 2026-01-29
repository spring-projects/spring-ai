/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.cassandra.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration properties for Cassandra Vector Store.
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@ConfigurationProperties(CassandraVectorStoreProperties.CONFIG_PREFIX)
public class CassandraVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.cassandra";

	private String keyspace = CassandraVectorStore.DEFAULT_KEYSPACE_NAME;

	private String table = CassandraVectorStore.DEFAULT_TABLE_NAME;

	private @Nullable String indexName = null;

	private String contentColumnName = CassandraVectorStore.DEFAULT_CONTENT_COLUMN_NAME;

	private String embeddingColumnName = CassandraVectorStore.DEFAULT_EMBEDDING_COLUMN_NAME;

	private int fixedThreadPoolExecutorSize = CassandraVectorStore.DEFAULT_ADD_CONCURRENCY;

	public String getKeyspace() {
		return this.keyspace;
	}

	public void setKeyspace(String keyspace) {
		this.keyspace = keyspace;
	}

	public String getTable() {
		return this.table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public @Nullable String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public String getContentColumnName() {
		return this.contentColumnName;
	}

	public void setContentColumnName(String contentColumnName) {
		this.contentColumnName = contentColumnName;
	}

	public String getEmbeddingColumnName() {
		return this.embeddingColumnName;
	}

	public void setEmbeddingColumnName(String embeddingColumnName) {
		this.embeddingColumnName = embeddingColumnName;
	}

	public int getFixedThreadPoolExecutorSize() {
		return this.fixedThreadPoolExecutorSize;
	}

	public void setFixedThreadPoolExecutorSize(int fixedThreadPoolExecutorSize) {
		Assert.state(0 < fixedThreadPoolExecutorSize, "Thread-pool size must be greater than zero");
		this.fixedThreadPoolExecutorSize = fixedThreadPoolExecutorSize;
	}

}
