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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Connection properties for the Google GenAI Gemini-TTS text-to-speech model backed by
 * the Cloud Text-to-Speech V2 API.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
@ConfigurationProperties(GoogleGenAiTextToSpeechConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiTextToSpeechConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.tts";

	/**
	 * The Google Cloud project ID (required).
	 */
	private @Nullable String projectId;

	/**
	 * The Text-to-Speech V2 location (e.g. {@code global}, {@code us}, {@code eu}).
	 */
	private @Nullable String location = "global";

	/**
	 * URI to Google Cloud credentials (optional; Application Default Credentials are used
	 * when unset).
	 */
	private @Nullable Resource credentialsUri;

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
