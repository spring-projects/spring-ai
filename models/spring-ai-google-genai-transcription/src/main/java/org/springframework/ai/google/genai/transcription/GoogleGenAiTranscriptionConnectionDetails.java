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

package org.springframework.ai.google.genai.transcription;

import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GoogleGenAiTranscriptionConnectionDetails represents the details of a connection to the
 * Google Gen AI (Gemini) API used for audio transcription. It constructs and exposes a
 * {@link Client} configured either for the Gemini Developer API (API key authentication)
 * or for Vertex AI (project, location and Google credentials authentication).
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public final class GoogleGenAiTranscriptionConnectionDetails {

	public static final String DEFAULT_LOCATION = "global";

	private final Client genAiClient;

	private GoogleGenAiTranscriptionConnectionDetails(Client genAiClient) {
		this.genAiClient = genAiClient;
	}

	public static Builder builder() {
		return new Builder();
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
		 * Sets the API key used to authenticate against the Gemini Developer API.
		 * @param apiKey the Gemini API key
		 * @return this builder
		 */
		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		/**
		 * Sets the Google Cloud project ID (Vertex AI mode).
		 * @param projectId the Google Cloud project ID
		 * @return this builder
		 */
		public Builder projectId(@Nullable String projectId) {
			this.projectId = projectId;
			return this;
		}

		/**
		 * Sets the Google Cloud location (Vertex AI mode). Defaults to
		 * {@link #DEFAULT_LOCATION} when unset.
		 * @param location the Google Cloud location
		 * @return this builder
		 */
		public Builder location(@Nullable String location) {
			this.location = location;
			return this;
		}

		/**
		 * Sets the Google credentials (Vertex AI mode). Application Default Credentials
		 * are used when unset.
		 * @param credentials the Google credentials
		 * @return this builder
		 */
		public Builder credentials(@Nullable GoogleCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		/**
		 * Sets a custom {@link Client}. If provided, all other connection settings are
		 * ignored.
		 * @param genAiClient the custom GenAI client
		 * @return this builder
		 */
		public Builder client(@Nullable Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		public GoogleGenAiTranscriptionConnectionDetails build() {
			if (Objects.nonNull(this.genAiClient)) {
				return new GoogleGenAiTranscriptionConnectionDetails(this.genAiClient);
			}

			final Client.Builder clientBuilder = Client.builder();
			if (StringUtils.hasText(this.apiKey)) {
				clientBuilder.apiKey(this.apiKey);
			}
			else {
				Assert.hasText(this.projectId,
						"Either an API key (Gemini Developer API) or a project ID (Vertex AI) must be provided");
				clientBuilder.project(this.projectId)
					.location(StringUtils.hasText(this.location) ? this.location : DEFAULT_LOCATION)
					.vertexAI(true);
				if (Objects.nonNull(this.credentials)) {
					clientBuilder.credentials(this.credentials);
				}
			}
			return new GoogleGenAiTranscriptionConnectionDetails(clientBuilder.build());
		}

	}

}
