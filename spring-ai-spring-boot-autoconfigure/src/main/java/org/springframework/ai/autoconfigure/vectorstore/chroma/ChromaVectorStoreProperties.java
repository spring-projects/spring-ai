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

package org.springframework.ai.autoconfigure.vectorstore.chroma;

import org.springframework.ai.autoconfigure.vectorstore.CommonVectorStoreProperties;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Chroma Vector Store.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 */
@ConfigurationProperties(ChromaVectorStoreProperties.CONFIG_PREFIX)
public class ChromaVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.chroma";

	private String collectionName = ChromaVectorStore.DEFAULT_COLLECTION_NAME;

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

}
