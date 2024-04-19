/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.cassandra;

import org.springframework.ai.vectorstore.CassandraVectorStoreConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@ConfigurationProperties(CassandraVectorStoreProperties.CONFIG_PREFIX)
public class CassandraVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.cassandra";

	private String cassandraContactPointHosts = null;

	private int cassandraContactPointPort = 9042;

	private String cassandraLocalDatacenter = null;

	private String keyspace = CassandraVectorStoreConfig.DEFAULT_KEYSPACE_NAME;

	private String table = CassandraVectorStoreConfig.DEFAULT_TABLE_NAME;

	private String indexName = CassandraVectorStoreConfig.DEFAULT_INDEX_NAME;

	private String contentColumnName = CassandraVectorStoreConfig.DEFAULT_CONTENT_COLUMN_NAME;

	private String embeddingColumnName = CassandraVectorStoreConfig.DEFAULT_EMBEDDING_COLUMN_NAME;

	private boolean disallowSchemaChanges = false;

	public String getCassandraContactPointHosts() {
		return this.cassandraContactPointHosts;
	}

	/** comma or space separated */
	public void setCassandraContactPointHosts(String cassandraContactPointHosts) {
		this.cassandraContactPointHosts = cassandraContactPointHosts;
	}

	public int getCassandraContactPointPort() {
		return this.cassandraContactPointPort;
	}

	public void setCassandraContactPointPort(int cassandraContactPointPort) {
		this.cassandraContactPointPort = cassandraContactPointPort;
	}

	public String getCassandraLocalDatacenter() {
		return this.cassandraLocalDatacenter;
	}

	public void setCassandraLocalDatacenter(String cassandraLocalDatacenter) {
		this.cassandraLocalDatacenter = cassandraLocalDatacenter;
	}

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

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getContentFieldName() {
		return this.contentColumnName;
	}

	public void setContentFieldName(String contentFieldName) {
		this.contentColumnName = contentFieldName;
	}

	public String getEmbeddingFieldName() {
		return this.embeddingColumnName;
	}

	public void setEmbeddingFieldName(String embeddingFieldName) {
		this.embeddingColumnName = embeddingFieldName;
	}

	public Boolean getDisallowSchemaCreation() {
		return this.disallowSchemaChanges;
	}

	public void setDisallowSchemaCreation(boolean disallowSchemaCreation) {
		this.disallowSchemaChanges = disallowSchemaCreation;
	}

}
