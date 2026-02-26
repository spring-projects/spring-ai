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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Google GenAI Image.
 *
 * @author Danil Temnikov
 */
@ConfigurationProperties(GoogleGenAiImageConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiImageConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai";

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

}
