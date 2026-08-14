/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Vertex AI Embedding.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiEmbeddingConnectionProperties.CONFIG_PREFIX)
public class VertexAiEmbeddingConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.embedding";

	/**
	 * Vertex AI Gemini project ID.
	 */
	private @Nullable String projectId;

	/**
	 * Vertex AI Gemini location.
	 */
	private @Nullable String location;

	/**
	 * URI to Vertex AI Gemini credentials (optional)
	 */
	private @Nullable Resource credentialsUri;

	/**
	 * Vertex AI Gemini API endpoint.
	 */
	private @Nullable String apiEndpoint;

	public @Nullable String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(@Nullable String projectId) {
		this.projectId = projectId;
	}

	public @Nullable String getLocation() {
		return this.location;
	}

	public void setLocation(@Nullable String location) {
		this.location = location;
	}

	public @Nullable Resource getCredentialsUri() {
		return this.credentialsUri;
	}

	public void setCredentialsUri(@Nullable Resource credentialsUri) {
		this.credentialsUri = credentialsUri;
	}

	public @Nullable String getApiEndpoint() {
		return this.apiEndpoint;
	}

	public void setApiEndpoint(@Nullable String apiEndpoint) {
		this.apiEndpoint = apiEndpoint;
	}

}
