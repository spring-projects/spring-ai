/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.infinispan.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Infinispan Vector Store.
 */
@ConfigurationProperties(prefix = InfinispanVectorStoreProperties.CONFIG_PREFIX)
public class InfinispanVectorStoreProperties extends CommonVectorStoreProperties {

	/**
	 * Configuration prefix for Spring AI VectorStore Infinispan.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.infinispan";

	private @Nullable Boolean registerSchema;

	private @Nullable Boolean createStore;

	private @Nullable String storeName;

	private @Nullable String storeConfig;

	private @Nullable Integer distance;

	private @Nullable String similarity;

	private @Nullable String schemaFileName;

	private @Nullable String packageName;

	private @Nullable String itemName;

	private @Nullable String metadataItemName;

	public @Nullable String getStoreName() {
		return this.storeName;
	}

	public void setStoreName(@Nullable String storeName) {
		this.storeName = storeName;
	}

	public @Nullable String getStoreConfig() {
		return this.storeConfig;
	}

	public void setStoreConfig(@Nullable String storeConfig) {
		this.storeConfig = storeConfig;
	}

	public @Nullable Integer getDistance() {
		return this.distance;
	}

	public void setDistance(@Nullable Integer distance) {
		this.distance = distance;
	}

	public @Nullable String getSimilarity() {
		return this.similarity;
	}

	public void setSimilarity(@Nullable String similarity) {
		this.similarity = similarity;
	}

	public @Nullable String getSchemaFileName() {
		return this.schemaFileName;
	}

	public void setSchemaFileName(@Nullable String schemaFileName) {
		this.schemaFileName = schemaFileName;
	}

	public @Nullable String getPackageName() {
		return this.packageName;
	}

	public void setPackageName(@Nullable String packageName) {
		this.packageName = packageName;
	}

	public @Nullable String getItemName() {
		return this.itemName;
	}

	public void setItemName(@Nullable String itemName) {
		this.itemName = itemName;
	}

	public @Nullable String getMetadataItemName() {
		return this.metadataItemName;
	}

	public void setMetadataItemName(@Nullable String metadataItemName) {
		this.metadataItemName = metadataItemName;
	}

	public @Nullable Boolean isRegisterSchema() {
		return this.registerSchema;
	}

	public void setRegisterSchema(@Nullable Boolean registerSchema) {
		this.registerSchema = registerSchema;
	}

	public @Nullable Boolean isCreateStore() {
		return this.createStore;
	}

	public void setCreateStore(@Nullable Boolean createStore) {
		this.createStore = createStore;
	}

}
