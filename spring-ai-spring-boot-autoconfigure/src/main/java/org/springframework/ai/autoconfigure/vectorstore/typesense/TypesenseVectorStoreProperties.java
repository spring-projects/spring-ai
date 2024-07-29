/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.ai.autoconfigure.vectorstore.CommonVectorStoreProperties;
import org.springframework.ai.vectorstore.TypesenseVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Pablo Sanchidrian Herrera
 * @author Soby Chacko
 */
@ConfigurationProperties(TypesenseVectorStoreProperties.CONFIG_PREFIX)
public class TypesenseVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.typesense";

	/**
	 * Typesense collection name to store the vectors.
	 */
	private String collectionName = TypesenseVectorStore.DEFAULT_COLLECTION_NAME;

	/**
	 * The dimension of the vectors to be stored in the Typesense collection.
	 */
	private int embeddingDimension = TypesenseVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;

	public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public void setEmbeddingDimension(int embeddingDimension) {
		this.embeddingDimension = embeddingDimension;
	}

}
