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

import com.google.genai.Client;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GoogleGenAiImageConnectionDetails represents the details of a connection to the image
 * generation service using the Google Gen AI SDK. It provides methods to create and
 * configure the GenAI {@link Client} instance using either the Gemini Developer API (via
 * API key) or Vertex AI (via Google Cloud project + location).
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public final class GoogleGenAiImageConnectionDetails {

	public static final String DEFAULT_LOCATION = "us-central1";

	/**
	 * Your Google Cloud project ID (required for Vertex AI mode).
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
	 * The API key for using the Gemini Developer API. If null, Vertex AI mode will be
	 * used.
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
	 * Returns the model endpoint name as expected by the Google GenAI SDK. The SDK
	 * handles the full endpoint construction internally, so we return the raw model name.
	 * @param modelName the model name (e.g., {@code imagen-4.0-generate-001})
	 * @return the model endpoint name
	 */
	public String getModelEndpointName(String modelName) {
		return modelName;
	}

	public static final class Builder {

		private @Nullable String projectId;

		private @Nullable String location;

		private @Nullable String apiKey;

		private @Nullable Client genAiClient;

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

		public GoogleGenAiImageConnectionDetails build() {
			// If a custom client is provided, use it directly
			if (this.genAiClient != null) {
				return new GoogleGenAiImageConnectionDetails(this.projectId, this.location, this.apiKey,
						this.genAiClient);
			}

			Client.Builder clientBuilder = Client.builder();

			if (StringUtils.hasText(this.apiKey)) {
				// Gemini Developer API mode
				clientBuilder.apiKey(this.apiKey);
			}
			else {
				// Vertex AI mode
				Assert.hasText(this.projectId, "Project ID must be provided for Vertex AI mode");

				if (!StringUtils.hasText(this.location)) {
					this.location = DEFAULT_LOCATION;
				}

				clientBuilder.project(this.projectId).location(this.location).vertexAI(true);
			}

			Client builtClient = clientBuilder.build();
			return new GoogleGenAiImageConnectionDetails(this.projectId, this.location, this.apiKey, builtClient);
		}

	}

}
