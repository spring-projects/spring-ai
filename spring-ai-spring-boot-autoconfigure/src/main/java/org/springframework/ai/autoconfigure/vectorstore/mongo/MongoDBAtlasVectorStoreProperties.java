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
package org.springframework.ai.autoconfigure.vectorstore.mongo;

import org.springframework.ai.autoconfigure.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(MongoDBAtlasVectorStoreProperties.CONFIG_PREFIX)
public class MongoDBAtlasVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.mongodb";

	/**
	 * The name of the collection to store the vectors. Defaults to "vector_store".
	 */
	private String collectionName;

	/**
	 * The name of the path to store the vectors. Defaults to "embedding".
	 */
	private String pathName;

	/**
	 * The name of the index to store the vectors. Defaults to "vector_index".
	 */
	private String indexName;

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getPathName() {
		return this.pathName;
	}

	public void setPathName(String pathName) {
		this.pathName = pathName;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

}
