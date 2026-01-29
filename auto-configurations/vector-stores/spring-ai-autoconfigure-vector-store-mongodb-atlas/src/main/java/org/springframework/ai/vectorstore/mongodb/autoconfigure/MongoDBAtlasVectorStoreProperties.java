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

package org.springframework.ai.vectorstore.mongodb.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MongoDB Atlas Vector Store.
 *
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @author Ignacio López
 * @since 1.0.0
 */
@ConfigurationProperties(MongoDBAtlasVectorStoreProperties.CONFIG_PREFIX)
public class MongoDBAtlasVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.mongodb";

	/**
	 * The name of the collection to store the vectors. Defaults to "vector_store".
	 */
	private @Nullable String collectionName;

	/**
	 * The name of the path to store the vectors. Defaults to "embedding".
	 */
	private @Nullable String pathName;

	/**
	 * The name of the index to store the vectors. Defaults to "vector_index".
	 */
	private @Nullable String indexName;

	/**
	 * Name of the metadata fields to use as filters.
	 */
	private List<String> metadataFieldsToFilter = List.of();

	public @Nullable String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(@Nullable String collectionName) {
		this.collectionName = collectionName;
	}

	public @Nullable String getPathName() {
		return this.pathName;
	}

	public void setPathName(@Nullable String pathName) {
		this.pathName = pathName;
	}

	public @Nullable String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public List<String> getMetadataFieldsToFilter() {
		return this.metadataFieldsToFilter;
	}

	public void setMetadataFieldsToFilter(List<String> metadataFieldsToFilter) {
		this.metadataFieldsToFilter = metadataFieldsToFilter;
	}

}
