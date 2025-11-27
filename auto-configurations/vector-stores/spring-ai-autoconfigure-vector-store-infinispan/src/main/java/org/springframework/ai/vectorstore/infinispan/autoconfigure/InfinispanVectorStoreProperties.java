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

	private Boolean registerSchema;

	private Boolean createStore;

	private String storeName;

	private String storeConfig;

	private Integer distance;

	private String similarity;

	private String schemaFileName;

	private String packageName;

	private String itemName;

	private String metadataItemName;

	public String getStoreName() {
		return this.storeName;
	}

	public void setStoreName(String storeName) {
		this.storeName = storeName;
	}

	public String getStoreConfig() {
		return this.storeConfig;
	}

	public void setStoreConfig(String storeConfig) {
		this.storeConfig = storeConfig;
	}

	public Integer getDistance() {
		return this.distance;
	}

	public void setDistance(Integer distance) {
		this.distance = distance;
	}

	public String getSimilarity() {
		return this.similarity;
	}

	public void setSimilarity(String similarity) {
		this.similarity = similarity;
	}

	public String getSchemaFileName() {
		return this.schemaFileName;
	}

	public void setSchemaFileName(String schemaFileName) {
		this.schemaFileName = schemaFileName;
	}

	public String getPackageName() {
		return this.packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getItemName() {
		return this.itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
	}

	public String getMetadataItemName() {
		return this.metadataItemName;
	}

	public void setMetadataItemName(String metadataItemName) {
		this.metadataItemName = metadataItemName;
	}

	public Boolean isRegisterSchema() {
		return this.registerSchema;
	}

	public void setRegisterSchema(Boolean registerSchema) {
		this.registerSchema = registerSchema;
	}

	public Boolean isCreateStore() {
		return this.createStore;
	}

	public void setCreateStore(boolean createStore) {
		this.createStore = createStore;
	}

}
