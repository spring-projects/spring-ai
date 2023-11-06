/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.azure;

import org.springframework.ai.vectorstore.AzureVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(AzureVectorStoreProperties.CONFIG_PREFIX)
public class AzureVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.azure";

	private String url;

	private String apiKey;

	private String indexName = AzureVectorStore.DEFAULT_INDEX_NAME;

	private int defaultTopK = -1;

	private double defaultSimilarityThreshold = -1;

	public String getUrl() {
		return url;
	}

	public void setUrl(String endpointUrl) {
		this.url = endpointUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getDefaultTopK() {
		return defaultTopK;
	}

	public void setDefaultTopK(int defaultTopK) {
		this.defaultTopK = defaultTopK;
	}

	public double getDefaultSimilarityThreshold() {
		return defaultSimilarityThreshold;
	}

	public void setDefaultSimilarityThreshold(double defaultSimilarityThreshold) {
		this.defaultSimilarityThreshold = defaultSimilarityThreshold;
	}

}
