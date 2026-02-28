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

import java.util.ArrayList;
import java.util.List;

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

	/**
	 * List of metadata fields that can be used in similarity search filter expressions.
	 * Each entry defines a field name and type (string, int32, int64, decimal, bool,
	 * date).
	 */
	private List<MetadataFieldEntry> metadataFields = new ArrayList<>();

	/**
	 * Configuration entry for a filterable metadata field.
	 */
	public static class MetadataFieldEntry {

		private String name;

		private String fieldType;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		/**
		 * Field type: string, int32, int64, decimal, bool, date.
		 */
		public String getFieldType() {
			return this.fieldType;
		}

		public void setFieldType(String fieldType) {
			this.fieldType = fieldType;
		}

	}

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

	public List<MetadataFieldEntry> getMetadataFields() {
		return this.metadataFields;
	}

	public void setMetadataFields(List<MetadataFieldEntry> metadataFields) {
		this.metadataFields = metadataFields != null ? metadataFields : new ArrayList<>();
	}

	/**
	 * Alias for {@link #setMetadataFields(List)} to support the common typo
	 * "metadata-fileds" in configuration. Both "metadata-fields" and
	 * "metadata-fileds" are accepted.
	 */
	public void setMetadataFileds(List<MetadataFieldEntry> metadataFileds) {
		setMetadataFields(metadataFileds);
	}

}
