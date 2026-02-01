/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for connecting to Anthropic models on Google Cloud Vertex AI.
 */
@ConfigurationProperties(VertexAiAnthropicConnectionProperties.CONFIG_PREFIX)
public class VertexAiAnthropicConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.anthropic";

	/**
	 * Google Cloud project ID for Vertex AI.
	 */
	public String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	private String projectId;

	/**
	 * Google Cloud region supporting Anthropic Claude models (e.g., "us-central1").
	 */
	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	private String location;

	/**
	 * URI to the Vertex AI credentials file (optional, uses default credentials if not
	 * set).
	 */
	public Resource getCredentialsUri() {
		return this.credentialsUri;
	}

	public void setCredentialsUri(Resource credentialsUri) {
		this.credentialsUri = credentialsUri;
	}

	private Resource credentialsUri;

	/**
	 * Custom Vertex AI API endpoint URL (optional, uses default if not set).
	 */
	public String getApiEndpoint() {
		return this.apiEndpoint;
	}

	public void setApiEndpoint(String apiEndpoint) {
		this.apiEndpoint = apiEndpoint;
	}

	private String apiEndpoint;

}
