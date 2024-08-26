/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vertexai.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Vertex AI Anthropic Chat.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiAnthropicConnectionProperties.CONFIG_PREFIX)
public class VertexAiAnthropicConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.anthropic";

	/**
	 * Vertex AI Anthropic project ID.
	 */
	private String projectId;

	/**
	 * Vertex AI Anthropic location.
	 */
	private String location;

	/**
	 * URI to Vertex AI Anthropic credentials (optional)
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
