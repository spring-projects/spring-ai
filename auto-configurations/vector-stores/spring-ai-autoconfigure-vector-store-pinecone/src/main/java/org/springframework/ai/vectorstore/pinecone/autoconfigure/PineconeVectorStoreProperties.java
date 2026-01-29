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

package org.springframework.ai.vectorstore.pinecone.autoconfigure;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Pinecone Vector Store.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@ConfigurationProperties(PineconeVectorStoreProperties.CONFIG_PREFIX)
public class PineconeVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.pinecone";

	private @Nullable String apiKey;

	private String environment = "gcp-starter";

	private @Nullable String projectId;

	private @Nullable String indexName;

	private String namespace = "";

	private String contentFieldName = PineconeVectorStore.CONTENT_FIELD_NAME;

	private String distanceMetadataFieldName = DocumentMetadata.DISTANCE.value();

	private Duration serverSideTimeout = Duration.ofSeconds(20);

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public String getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public @Nullable String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(@Nullable String projectId) {
		this.projectId = projectId;
	}

	public String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public @Nullable String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public Duration getServerSideTimeout() {
		return this.serverSideTimeout;
	}

	public void setServerSideTimeout(Duration serverSideTimeout) {
		this.serverSideTimeout = serverSideTimeout;
	}

	public String getContentFieldName() {
		return this.contentFieldName;
	}

	public void setContentFieldName(String contentFieldName) {
		this.contentFieldName = contentFieldName;
	}

	public String getDistanceMetadataFieldName() {
		return this.distanceMetadataFieldName;
	}

	public void setDistanceMetadataFieldName(String distanceMetadataFieldName) {
		this.distanceMetadataFieldName = distanceMetadataFieldName;
	}

}
