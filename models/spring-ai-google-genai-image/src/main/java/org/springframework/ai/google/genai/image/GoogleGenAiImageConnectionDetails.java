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

package org.springframework.ai.google.genai.image;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GoogleGenAiImageConnectionDetails represents the details of a connection to the image
 * service using the new Google Gen AI SDK. It provides methods to create and configure
 * the GenAI Client instance.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public final class GoogleGenAiImageConnectionDetails {

	public static final String DEFAULT_LOCATION = "us-central1";

	/**
	 * Your project ID.
	 */
	private final @Nullable String projectId;

	/**
	 * A location is a <a href="https://cloud.google.com/about/locations?hl=en">region</a>
	 * you can specify in a request to control where data is stored at rest. For a list of
	 * available regions, see <a href=
	 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations?hl=en">Generative
	 * AI on Vertex AI locations</a>.
	 */
	private final @Nullable String location;

	/**
	 * The API key for using Gemini Developer API. If null, Vertex AI mode will be used.
	 */
	private final @Nullable String apiKey;

	/**
	 * The GenAI Client instance configured for this connection.
	 */
	private final Client genAiClient;

	private GoogleGenAiImageConnectionDetails(@Nullable String projectId, @Nullable String location,
			@Nullable String apiKey, Client genAiClient) {
		this.projectId = projectId;
		this.location = location;
		this.apiKey = apiKey;
		this.genAiClient = genAiClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getProjectId() {
		return this.projectId;
	}

	public @Nullable String getLocation() {
		return this.location;
	}

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public Client getGenAiClient() {
		return this.genAiClient;
	}

	/**
	 * Constructs the model endpoint name in the format expected by the image models.
	 * @param modelName the model name (e.g., "gemini-2.5-flash-image")
	 * @return the full model endpoint name
	 */
	public String getModelEndpointName(String modelName) {
		// For the new SDK, we just return the model name as is
		// The SDK handles the full endpoint construction internally
		return modelName;
	}

	public static final class Builder {

		/**
		 * Your project ID.
		 */
		private @Nullable String projectId;

		/**
		 * A location is a
		 * <a href="https://cloud.google.com/about/locations?hl=en">region</a> you can
		 * specify in a request to control where data is stored at rest. For a list of
		 * available regions, see <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations?hl=en">Generative
		 * AI on Vertex AI locations</a>.
		 */
		private @Nullable String location;

		/**
		 * The API key for using Gemini Developer API. If null, Vertex AI mode will be
		 * used.
		 */
		private @Nullable String apiKey;

		/**
		 * Custom GenAI client instance. If provided, other settings will be ignored.
		 */
		private @Nullable Client genAiClient;

		/**
		 * Google credentials to use for Vertex AI mode authentication. If provided, it is
		 * passed to the underlying {@link Client.Builder}.
		 */
		private @Nullable GoogleCredentials credentials;

		public Builder projectId(@Nullable String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder location(@Nullable String location) {
			this.location = location;
			return this;
		}

		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder genAiClient(@Nullable Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		/**
		 * Sets the {@link GoogleCredentials} to use for Vertex AI mode authentication.
		 * @param credentials the Google credentials
		 * @return this builder
		 */
		public Builder credentials(@Nullable GoogleCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public GoogleGenAiImageConnectionDetails build() {
			// If a custom client is provided, use it directly
			if (this.genAiClient != null) {
				return new GoogleGenAiImageConnectionDetails(this.projectId, this.location, this.apiKey,
						this.genAiClient);
			}

			// Otherwise, build a new client
			Client.Builder clientBuilder = Client.builder();

			if (StringUtils.hasText(this.apiKey)) {
				// Use Gemini Developer API mode
				clientBuilder.apiKey(this.apiKey);
			}
			else {
				// Use Vertex AI mode
				Assert.hasText(this.projectId, "Project ID must be provided for Vertex AI mode");

				if (!StringUtils.hasText(this.location)) {
					this.location = DEFAULT_LOCATION;
				}

				clientBuilder.project(this.projectId).location(this.location).vertexAI(true);

				if (this.credentials != null) {
					clientBuilder.credentials(this.credentials);
				}
			}

			return new GoogleGenAiImageConnectionDetails(this.projectId, this.location, this.apiKey,
					clientBuilder.build());
		}

	}

}
