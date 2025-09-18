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

package org.springframework.ai.google.genai;

import com.google.genai.Client;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GoogleGenAiEmbeddingConnectionDetails represents the details of a connection to the
 * embedding service using the new Google Gen AI SDK. It provides methods to create and
 * configure the GenAI Client instance.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Dan Dobrin
 * @since 1.0.0
 */
public final class GoogleGenAiEmbeddingConnectionDetails {

	public static final String DEFAULT_LOCATION = "us-central1";

	public static final String DEFAULT_PUBLISHER = "google";

	/**
	 * Your project ID.
	 */
	private final String projectId;

	/**
	 * A location is a <a href="https://cloud.google.com/about/locations?hl=en">region</a>
	 * you can specify in a request to control where data is stored at rest. For a list of
	 * available regions, see <a href=
	 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations?hl=en">Generative
	 * AI on Vertex AI locations</a>.
	 */
	private final String location;

	/**
	 * The API key for using Gemini Developer API. If null, Vertex AI mode will be used.
	 */
	private final String apiKey;

	/**
	 * The GenAI Client instance configured for this connection.
	 */
	private final Client genAiClient;

	private GoogleGenAiEmbeddingConnectionDetails(String projectId, String location, String apiKey,
			Client genAiClient) {
		this.projectId = projectId;
		this.location = location;
		this.apiKey = apiKey;
		this.genAiClient = genAiClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getProjectId() {
		return this.projectId;
	}

	public String getLocation() {
		return this.location;
	}

	public String getApiKey() {
		return this.apiKey;
	}

	public Client getGenAiClient() {
		return this.genAiClient;
	}

	/**
	 * Constructs the model endpoint name in the format expected by the embedding models.
	 * @param modelName the model name (e.g., "text-embedding-004")
	 * @return the full model endpoint name
	 */
	public String getModelEndpointName(String modelName) {
		// For the new SDK, we just return the model name as is
		// The SDK handles the full endpoint construction internally
		return modelName;
	}

	public static class Builder {

		/**
		 * Your project ID.
		 */
		private String projectId;

		/**
		 * A location is a
		 * <a href="https://cloud.google.com/about/locations?hl=en">region</a> you can
		 * specify in a request to control where data is stored at rest. For a list of
		 * available regions, see <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations?hl=en">Generative
		 * AI on Vertex AI locations</a>.
		 */
		private String location;

		/**
		 * The API key for using Gemini Developer API. If null, Vertex AI mode will be
		 * used.
		 */
		private String apiKey;

		/**
		 * Custom GenAI client instance. If provided, other settings will be ignored.
		 */
		private Client genAiClient;

		public Builder projectId(String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder location(String location) {
			this.location = location;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder genAiClient(Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		public GoogleGenAiEmbeddingConnectionDetails build() {
			// If a custom client is provided, use it directly
			if (this.genAiClient != null) {
				return new GoogleGenAiEmbeddingConnectionDetails(this.projectId, this.location, this.apiKey,
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
			}

			Client builtClient = clientBuilder.build();
			return new GoogleGenAiEmbeddingConnectionDetails(this.projectId, this.location, this.apiKey, builtClient);
		}

	}

}
