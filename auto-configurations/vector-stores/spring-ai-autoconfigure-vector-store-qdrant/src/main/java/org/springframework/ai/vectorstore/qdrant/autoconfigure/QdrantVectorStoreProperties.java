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

package org.springframework.ai.vectorstore.qdrant.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Qdrant Vector Store.
 *
 * @author Anush Shetty
 * @author Josh Long
 * @since 0.8.1
 */
@ConfigurationProperties(QdrantVectorStoreProperties.CONFIG_PREFIX)
public class QdrantVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.qdrant";

	/**
	 * The name of the collection to use in Qdrant.
	 */
	private String collectionName = QdrantVectorStore.DEFAULT_COLLECTION_NAME;

	/**
	 * The name of the content field to use in Qdrant.
	 */
	private String contentFieldName = QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME;

	/**
	 * The host of the Qdrant server.
	 */
	private String host = "localhost";

	/**
	 * The port of the Qdrant server.
	 */
	private int port = 6334;

	/**
	 * Whether to use TLS(HTTPS). Defaults to false.
	 */
	private boolean useTls = false;

	/**
	 * The API key to use for authentication with the Qdrant server.
	 */
	private @Nullable String apiKey = null;

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getContentFieldName() {
		return this.contentFieldName;
	}

	public void setContentFieldName(String contentFieldName) {
		this.contentFieldName = contentFieldName;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isUseTls() {
		return this.useTls;
	}

	public void setUseTls(boolean useTls) {
		this.useTls = useTls;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

}
