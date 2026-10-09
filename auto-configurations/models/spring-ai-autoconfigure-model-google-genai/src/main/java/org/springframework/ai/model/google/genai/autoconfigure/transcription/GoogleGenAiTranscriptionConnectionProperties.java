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

package org.springframework.ai.model.google.genai.autoconfigure.transcription;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Connection properties for the Google GenAI speech-to-text model, backed by the Google
 * GenAI SDK ({@code com.google.genai}).
 * <p>
 * Two authentication modes are supported: the Gemini Developer API ({@code api-key} only)
 * and Vertex AI ({@code project-id} + {@code location} with Application Default
 * Credentials or an explicit service account via {@code credentials-uri}).
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@ConfigurationProperties(GoogleGenAiTranscriptionConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiTranscriptionConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.transcription";

	/**
	 * The API key for the Gemini Developer API. When set, Vertex AI settings are ignored.
	 */
	private @Nullable String apiKey;

	/**
	 * The Google Cloud project ID (required).
	 */
	private @Nullable String projectId;

	/**
	 * The Vertex AI location (e.g. {@code global}, {@code us}, {@code eu}).
	 */
	private @Nullable String location = "global";

	/**
	 * URI to Google Cloud credentials (optional; Application Default Credentials are used
	 * when unset). Only applies to Vertex AI.
	 */
	private @Nullable Resource credentialsUri;

	/**
	 * Whether to use Vertex AI mode. If false, uses Gemini Developer API mode. This is
	 * automatically determined based on whether apiKey or projectId is set.
	 */
	private boolean vertexAi;

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

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

	public boolean isVertexAi() {
		return this.vertexAi;
	}

	public void setVertexAi(boolean vertexAi) {
		this.vertexAi = vertexAi;
	}

}
