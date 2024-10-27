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

package org.springframework.ai.vertexai.embedding;

import java.io.IOException;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;

import org.springframework.util.StringUtils;

/**
 * VertexAiEmbeddingConnectionDetails represents the details of a connection to the Vertex
 * AI embedding service. It provides methods to access the project ID, location,
 * publisher, and PredictionServiceSettings.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
public class VertexAiEmbeddingConnectionDetails {

	public static final String DEFAULT_ENDPOINT = "us-central1-aiplatform.googleapis.com:443";

	public static final String DEFAULT_ENDPOINT_SUFFIX = "-aiplatform.googleapis.com:443";

	public static final String DEFAULT_PUBLISHER = "google";

	private static final String DEFAULT_LOCATION = "us-central1";

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

	private final String publisher;

	private PredictionServiceSettings predictionServiceSettings;

	public VertexAiEmbeddingConnectionDetails(String endpoint, String projectId, String location, String publisher) {
		this.projectId = projectId;
		this.location = location;
		this.publisher = publisher;

		try {
			this.predictionServiceSettings = PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
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

	public String getPublisher() {
		return this.publisher;
	}

	public EndpointName getEndpointName(String modelName) {
		return EndpointName.ofProjectLocationPublisherModelName(this.projectId, this.location, this.publisher,
				modelName);
	}

	public PredictionServiceSettings getPredictionServiceSettings() {
		return this.predictionServiceSettings;
	}

	public static class Builder {

		/**
		 * The Vertex AI embedding endpoint.
		 */
		private String endpoint;

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
		 *
		 */
		private String publisher;

		public Builder withApiEndpoint(String endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		public Builder withProjectId(String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder withLocation(String location) {
			this.location = location;
			return this;
		}

		public Builder withPublisher(String publisher) {
			this.publisher = publisher;
			return this;
		}

		public VertexAiEmbeddingConnectionDetails build() {
			if (!StringUtils.hasText(this.endpoint)) {
				if (!StringUtils.hasText(this.location)) {
					this.endpoint = DEFAULT_ENDPOINT;
					this.location = DEFAULT_LOCATION;
				}
				else {
					this.endpoint = this.location + DEFAULT_ENDPOINT_SUFFIX;
				}
			}

			if (!StringUtils.hasText(this.publisher)) {
				this.publisher = DEFAULT_PUBLISHER;
			}

			return new VertexAiEmbeddingConnectionDetails(this.endpoint, this.projectId, this.location, this.publisher);
		}

	}

}
