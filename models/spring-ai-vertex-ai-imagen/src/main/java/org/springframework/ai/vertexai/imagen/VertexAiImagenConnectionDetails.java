/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vertexai.imagen;

import java.io.IOException;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;

import org.springframework.util.StringUtils;

/**
 * <b>VertexAiImagenConnectionDetails</b> represents the details of a connection to the
 * Vertex AI imagen service. It provides methods to access the project ID, location,
 * publisher, and PredictionServiceSettings.
 *
 * @author Sami Marzouki
 */
public class VertexAiImagenConnectionDetails {

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

	private final PredictionServiceSettings predictionServiceSettings;

	public VertexAiImagenConnectionDetails(String projectId, String location, String publisher,
			PredictionServiceSettings predictionServiceSettings) {
		this.projectId = projectId;
		this.location = location;
		this.publisher = publisher;
		this.predictionServiceSettings = predictionServiceSettings;
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

	public com.google.cloud.aiplatform.v1.PredictionServiceSettings getPredictionServiceSettings() {
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

		/**
		 * Allows the connection settings to be customised
		 */
		private PredictionServiceSettings predictionServiceSettings;

		public Builder apiEndpoint(String endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		public Builder projectId(String projectId) {
			this.projectId = projectId;
			return this;
		}

		public Builder location(String location) {
			this.location = location;
			return this;
		}

		public Builder publisher(String publisher) {
			this.publisher = publisher;
			return this;
		}

		public Builder predictionServiceSettings(PredictionServiceSettings predictionServiceSettings) {
			this.predictionServiceSettings = predictionServiceSettings;
			return this;
		}

		public VertexAiImagenConnectionDetails build() {
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

			if (this.predictionServiceSettings == null) {
				try {
					this.predictionServiceSettings = PredictionServiceSettings.newBuilder()
						.setEndpoint(this.endpoint)
						.build();
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			return new VertexAiImagenConnectionDetails(this.projectId, this.location, this.publisher,
					this.predictionServiceSettings);
		}

	}

}
