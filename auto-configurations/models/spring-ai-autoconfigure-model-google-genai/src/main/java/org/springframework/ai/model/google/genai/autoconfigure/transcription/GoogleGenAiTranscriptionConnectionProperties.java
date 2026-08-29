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
 * Connection properties for the Google GenAI Transcription (Speech-to-Text V2).
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@ConfigurationProperties(GoogleGenAiTranscriptionConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiTranscriptionConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.transcription";

	/**
	 * Google GenAI API Key used to authenticate Speech-to-Text V2 requests.
	 */
	private @Nullable String apiKey;

	/**
	 * Google Cloud project ID. Required by Speech-to-Text V2 resource names.
	 */
	private @Nullable String projectId;

	/**
	 * Speech-to-Text V2 location (e.g. {@code global}, {@code us}, {@code eu}).
	 */
	private @Nullable String location;

	/**
	 * URI to Google Cloud credentials (optional; used when no API key is provided).
	 */
	private @Nullable Resource credentialsUri;

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

}
