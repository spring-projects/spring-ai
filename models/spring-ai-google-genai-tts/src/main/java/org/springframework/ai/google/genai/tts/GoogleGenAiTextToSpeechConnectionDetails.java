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

package org.springframework.ai.google.genai.tts;

import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Holds the connection details for the Gemini API used by Gemini-TTS and builds the
 * underlying Google GenAI {@link Client}.
 * <p>
 * Two authentication modes are supported:
 * <ul>
 * <li><b>Gemini Developer API</b>: an API key only ({@link Builder#apiKey(String)}).</li>
 * <li><b>Vertex AI</b>: a {@code projectId} and {@code location}, authenticated through
 * Application Default Credentials (ADC) or an explicit {@link GoogleCredentials} service
 * account.</li>
 * </ul>
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public final class GoogleGenAiTextToSpeechConnectionDetails {

	public static final String DEFAULT_LOCATION = "global";

	private final @Nullable String projectId;

	private final String location;

	private final @Nullable String apiKey;

	private final Client genAiClient;

	private GoogleGenAiTextToSpeechConnectionDetails(@Nullable String projectId, String location,
			@Nullable String apiKey, Client genAiClient) {
		this.projectId = projectId;
		this.location = location;
		this.apiKey = apiKey;
		this.genAiClient = genAiClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The Google Cloud project ID, or {@code null} when the Gemini Developer API (API
	 * key) is used.
	 * @return the project ID, if any
	 */
	public @Nullable String getProjectId() {
		return this.projectId;
	}

	/**
	 * The Vertex AI location. Defaults to {@value #DEFAULT_LOCATION}.
	 * @return the location
	 */
	public String getLocation() {
		return this.location;
	}

	/**
	 * The Gemini Developer API key, or {@code null} when Vertex AI is used.
	 * @return the API key, if any
	 */
	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public Client getGenAiClient() {
		return this.genAiClient;
	}

	public static final class Builder {

		private @Nullable String apiKey;

		private @Nullable String projectId;

		private @Nullable String location;

		private @Nullable GoogleCredentials credentials;

		private @Nullable Client genAiClient;

		private Builder() {
		}

		/**
		 * Sets the API key for the Gemini Developer API. When set, {@code projectId},
		 * {@code location} and {@code credentials} are ignored.
		 * @param apiKey the Gemini API key
		 * @return this builder
		 */
		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder projectId(@Nullable String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder location(@Nullable String location) {
			this.location = location;
			return this;
		}

		public Builder credentials(@Nullable GoogleCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		/**
		 * Sets an explicit {@link Client} to use. If provided, all other settings are
		 * ignored.
		 * @param genAiClient the client to use
		 * @return this builder
		 */
		public Builder genAiClient(@Nullable Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		public GoogleGenAiTextToSpeechConnectionDetails build() {
			this.location = StringUtils.hasText(this.location) ? this.location : DEFAULT_LOCATION;

			if (Objects.nonNull(this.genAiClient)) {
				return new GoogleGenAiTextToSpeechConnectionDetails(this.projectId, this.location, this.apiKey,
						this.genAiClient);
			}

			if (StringUtils.hasText(this.apiKey)) {
				// Gemini Developer API mode: projectId, location and credentials are
				// ignored.
				Client apiKeyClient = Client.builder().apiKey(this.apiKey).build();
				return new GoogleGenAiTextToSpeechConnectionDetails(this.projectId, this.location, this.apiKey,
						apiKeyClient);
			}

			Assert.hasText(this.projectId, "Project ID must be provided for Vertex AI mode");

			Client.Builder clientBuilder = Client.builder()
				.project(this.projectId)
				.location(this.location)
				.vertexAI(true);
			if (Objects.nonNull(this.credentials)) {
				clientBuilder.credentials(this.credentials);
			}
			return new GoogleGenAiTextToSpeechConnectionDetails(this.projectId, this.location, this.apiKey,
					clientBuilder.build());
		}

	}

}
