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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Google GenAI Embedding Connection.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiEmbeddingConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiEmbeddingConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.embedding";

	/**
	 * Google GenAI API Key (for Gemini Developer API mode).
	 */
	private String apiKey;

	/**
	 * Google Cloud project ID (for Vertex AI mode).
	 */
	private String projectId;

	/**
	 * Google Cloud location (for Vertex AI mode).
	 */
	private String location;

	/**
	 * URI to Google Cloud credentials (optional, for Vertex AI mode).
	 */
	private Resource credentialsUri;

	/**
	 * Timeout for the request in milliseconds.
	 */
	private Duration timeout;

	/**
	 * The maximum number of connections allowed in the pool.
	 */
	private Integer maxConnections;

	/**
	 * The maximum number of connections allowed per host.
	 */
	private Integer maxConnectionsPerHost;

	private Map<String, String> customHeaders = new HashMap<>();

	/**
	 * Whether to use Vertex AI mode. If false, uses Gemini Developer API mode. This is
	 * automatically determined based on whether apiKey or projectId is set.
	 */
	private boolean vertexAi;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Resource getCredentialsUri() {
		return this.credentialsUri;
	}

	public void setCredentialsUri(Resource credentialsUri) {
		this.credentialsUri = credentialsUri;
	}

	public boolean isVertexAi() {
		return this.vertexAi;
	}

	public void setVertexAi(boolean vertexAi) {
		this.vertexAi = vertexAi;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public Integer getMaxConnections() {
		return this.maxConnections;
	}

	public void setMaxConnections(Integer maxConnections) {
		this.maxConnections = maxConnections;
	}

	public Integer getMaxConnectionsPerHost() {
		return this.maxConnectionsPerHost;
	}

	public void setMaxConnectionsPerHost(Integer maxConnectionsPerHost) {
		this.maxConnectionsPerHost = maxConnectionsPerHost;
	}

	public Map<String, String> getCustomHeaders() {
		return this.customHeaders;
	}

	public void setCustomHeaders(Map<String, String> customHeaders) {
		this.customHeaders = customHeaders;
	}

}
