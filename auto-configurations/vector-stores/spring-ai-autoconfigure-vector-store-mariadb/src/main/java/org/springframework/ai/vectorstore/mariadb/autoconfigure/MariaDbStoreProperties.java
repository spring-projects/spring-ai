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

package org.springframework.ai.vectorstore.mariadb.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Diego Dupin
 */
@ConfigurationProperties(MariaDbStoreProperties.CONFIG_PREFIX)
public class MariaDbStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.mariadb";

	private int dimensions = MariaDBVectorStore.INVALID_EMBEDDING_DIMENSION;

	private MariaDBDistanceType distanceType = MariaDBDistanceType.COSINE;

	private boolean removeExistingVectorStoreTable = false;

	private String tableName = MariaDBVectorStore.DEFAULT_TABLE_NAME;

	private @Nullable String schemaName = null;

	private String embeddingFieldName = MariaDBVectorStore.DEFAULT_COLUMN_EMBEDDING;

	private String idFieldName = MariaDBVectorStore.DEFAULT_COLUMN_ID;

	private String metadataFieldName = MariaDBVectorStore.DEFAULT_COLUMN_METADATA;

	private String contentFieldName = MariaDBVectorStore.DEFAULT_COLUMN_CONTENT;

	private boolean schemaValidation = MariaDBVectorStore.DEFAULT_SCHEMA_VALIDATION;

	private int maxDocumentBatchSize = MariaDBVectorStore.MAX_DOCUMENT_BATCH_SIZE;

	public int getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	public MariaDBVectorStore.MariaDBDistanceType getDistanceType() {
		return this.distanceType;
	}

	public void setDistanceType(MariaDBDistanceType distanceType) {
		this.distanceType = distanceType;
	}

	public boolean isRemoveExistingVectorStoreTable() {
		return this.removeExistingVectorStoreTable;
	}

	public void setRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
	}

	public String getTableName() {
		return this.tableName;
	}

	public void setTableName(String vectorTableName) {
		this.tableName = vectorTableName;
	}

	public @Nullable String getSchemaName() {
		return this.schemaName;
	}

	public void setSchemaName(@Nullable String schemaName) {
		this.schemaName = schemaName;
	}

	public boolean isSchemaValidation() {
		return this.schemaValidation;
	}

	public void setSchemaValidation(boolean schemaValidation) {
		this.schemaValidation = schemaValidation;
	}

	public int getMaxDocumentBatchSize() {
		return this.maxDocumentBatchSize;
	}

	public void setMaxDocumentBatchSize(int maxDocumentBatchSize) {
		this.maxDocumentBatchSize = maxDocumentBatchSize;
	}

	public String getEmbeddingFieldName() {
		return this.embeddingFieldName;
	}

	public void setEmbeddingFieldName(String embeddingFieldName) {
		this.embeddingFieldName = embeddingFieldName;
	}

	public String getIdFieldName() {
		return this.idFieldName;
	}

	public void setIdFieldName(String idFieldName) {
		this.idFieldName = idFieldName;
	}

	public String getMetadataFieldName() {
		return this.metadataFieldName;
	}

	public void setMetadataFieldName(String metadataFieldName) {
		this.metadataFieldName = metadataFieldName;
	}

	public String getContentFieldName() {
		return this.contentFieldName;
	}

	public void setContentFieldName(String contentFieldName) {
		this.contentFieldName = contentFieldName;
	}

}
