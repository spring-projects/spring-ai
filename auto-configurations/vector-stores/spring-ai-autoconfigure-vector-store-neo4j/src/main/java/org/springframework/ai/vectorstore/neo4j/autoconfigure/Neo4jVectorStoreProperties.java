/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.neo4j.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Neo4j Vector Store.
 *
 * @author Jingzhou Ou
 * @author Josh Long
 */
@ConfigurationProperties(Neo4jVectorStoreProperties.CONFIG_PREFIX)
public class Neo4jVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.neo4j";

	/** Vector index query strategy; SEARCH requires Neo4j 2026.01 or later. */
	private Neo4jVectorStore.SearchStrategy searchStrategy = Neo4jVectorStore.SearchStrategy.VECTOR_QUERY;

	/**
	 * Logical metadata keys indexed for SEARCH filtering, without the metadata. prefix.
	 */
	private List<String> filterableMetadataFields = List.of();

	private @Nullable String databaseName;

	private @Nullable Integer embeddingDimension;

	private Neo4jVectorStore.Neo4jDistanceType distanceType = Neo4jVectorStore.Neo4jDistanceType.COSINE;

	private String label = Neo4jVectorStore.DEFAULT_LABEL;

	private String embeddingProperty = Neo4jVectorStore.DEFAULT_EMBEDDING_PROPERTY;

	private String indexName = Neo4jVectorStore.DEFAULT_INDEX_NAME;

	private String idProperty = Neo4jVectorStore.DEFAULT_ID_PROPERTY;

	private String constraintName = Neo4jVectorStore.DEFAULT_CONSTRAINT_NAME;

	private String textProperty = Neo4jVectorStore.DEFAULT_TEXT_PROPERTY;

	/**
	 * @return the vector index query strategy
	 * @since 2.1.0
	 */
	public Neo4jVectorStore.SearchStrategy getSearchStrategy() {
		return this.searchStrategy;
	}

	/**
	 * @param searchStrategy the vector index query strategy
	 * @since 2.1.0
	 */
	public void setSearchStrategy(Neo4jVectorStore.SearchStrategy searchStrategy) {
		this.searchStrategy = searchStrategy;
	}

	/**
	 * @return the metadata keys indexed for SEARCH filtering
	 * @since 2.1.0
	 */
	public List<String> getFilterableMetadataFields() {
		return this.filterableMetadataFields;
	}

	/**
	 * @param filterableMetadataFields the logical metadata keys
	 * @since 2.1.0
	 */
	public void setFilterableMetadataFields(List<String> filterableMetadataFields) {
		this.filterableMetadataFields = filterableMetadataFields;
	}

	public @Nullable String getDatabaseName() {
		return this.databaseName;
	}

	public void setDatabaseName(@Nullable String databaseName) {
		this.databaseName = databaseName;
	}

	public @Nullable Integer getEmbeddingDimension() {
		return this.embeddingDimension;
	}

	public void setEmbeddingDimension(@Nullable Integer embeddingDimension) {
		this.embeddingDimension = embeddingDimension;
	}

	public Neo4jVectorStore.Neo4jDistanceType getDistanceType() {
		return this.distanceType;
	}

	public void setDistanceType(Neo4jVectorStore.Neo4jDistanceType distanceType) {
		this.distanceType = distanceType;
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getEmbeddingProperty() {
		return this.embeddingProperty;
	}

	public void setEmbeddingProperty(String embeddingProperty) {
		this.embeddingProperty = embeddingProperty;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getIdProperty() {
		return this.idProperty;
	}

	public void setIdProperty(String idProperty) {
		this.idProperty = idProperty;
	}

	public String getConstraintName() {
		return this.constraintName;
	}

	public void setConstraintName(String constraintName) {
		this.constraintName = constraintName;
	}

	public String getTextProperty() {
		return this.textProperty;
	}

	public void setTextProperty(String textProperty) {
		this.textProperty = textProperty;
	}

}
