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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Azure Vector Store.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
@ConfigurationProperties(AzureVectorStoreProperties.CONFIG_PREFIX)
public class AzureVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.azure";

	private @Nullable String url;

	private @Nullable String apiKey;

	private String indexName = AzureVectorStore.DEFAULT_INDEX_NAME;

	private int defaultTopK = -1;

	private double defaultSimilarityThreshold = -1;

	private boolean useKeylessAuth;

	private @Nullable String contentFieldName;

	private @Nullable String embeddingFieldName;

	private @Nullable String metadataFieldName;

	public @Nullable String getUrl() {
		return this.url;
	}

	public void setUrl(@Nullable String endpointUrl) {
		this.url = endpointUrl;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getDefaultTopK() {
		return this.defaultTopK;
	}

	public void setDefaultTopK(int defaultTopK) {
		this.defaultTopK = defaultTopK;
	}

	public double getDefaultSimilarityThreshold() {
		return this.defaultSimilarityThreshold;
	}

	public void setDefaultSimilarityThreshold(double defaultSimilarityThreshold) {
		this.defaultSimilarityThreshold = defaultSimilarityThreshold;
	}

	public boolean isUseKeylessAuth() {
		return this.useKeylessAuth;
	}

	public void setUseKeylessAuth(boolean useKeylessAuth) {
		this.useKeylessAuth = useKeylessAuth;
	}

	public @Nullable String getContentFieldName() {
		return this.contentFieldName;
	}

	public void setContentFieldName(@Nullable String contentFieldName) {
		this.contentFieldName = contentFieldName;
	}

	public @Nullable String getEmbeddingFieldName() {
		return this.embeddingFieldName;
	}

	public void setEmbeddingFieldName(@Nullable String embeddingFieldName) {
		this.embeddingFieldName = embeddingFieldName;
	}

	public @Nullable String getMetadataFieldName() {
		return this.metadataFieldName;
	}

	public void setMetadataFieldName(@Nullable String metadataFieldName) {
		this.metadataFieldName = metadataFieldName;
	}

}
