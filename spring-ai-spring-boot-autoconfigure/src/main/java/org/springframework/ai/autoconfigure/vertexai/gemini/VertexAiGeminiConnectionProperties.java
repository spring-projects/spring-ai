/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(VertexAiGeminiConnectionProperties.CONFIG_PREFIX)
public class VertexAiGeminiConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.gemini";

	/**
	 * Vertex AI Gemini project ID.
	 */
	private String projectId;

	/**
	 * Vertex AI Gemini location.
	 */
	private String location;

	/**
	 * URI to Vertex AI Gemini credentials (optional)
	 */
	private Resource credentialsUri;

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

}
