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

package org.springframework.ai.autoconfigure.vectorstore.weaviate;

import java.util.Map;

import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.ConsistentLevel;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.MetadataField;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Weaviate Vector Store.
 *
 * @author Christian Tzolov
 */
@ConfigurationProperties(WeaviateVectorStoreProperties.CONFIG_PREFIX)
public class WeaviateVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.weaviate";

	private String scheme = "http";

	private String host = "localhost:8080";

	private String apiKey = "";

	private String objectClass = "SpringAiWeaviate";

	private ConsistentLevel consistencyLevel = WeaviateVectorStore.ConsistentLevel.ONE;

	/**
	 * spring.ai.vectorstore.weaviate.filter-field.<field-name>=<field-type>
	 */
	private Map<String, MetadataField.Type> filterField = Map.of();

	private Map<String, String> headers = Map.of();

	public String getScheme() {
		return this.scheme;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getObjectClass() {
		return this.objectClass;
	}

	public void setObjectClass(String indexName) {
		this.objectClass = indexName;
	}

	public ConsistentLevel getConsistencyLevel() {
		return this.consistencyLevel;
	}

	public void setConsistencyLevel(ConsistentLevel consistencyLevel) {
		this.consistencyLevel = consistencyLevel;
	}

	public Map<String, String> getHeaders() {
		return this.headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public Map<String, MetadataField.Type> getFilterField() {
		return this.filterField;
	}

	public void setFilterField(Map<String, MetadataField.Type> filterMetadataFields) {
		this.filterField = filterMetadataFields;
	}

}
