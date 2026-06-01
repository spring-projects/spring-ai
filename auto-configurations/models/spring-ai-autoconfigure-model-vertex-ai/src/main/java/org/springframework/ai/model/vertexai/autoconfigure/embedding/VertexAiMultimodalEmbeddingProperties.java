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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiMultimodalEmbeddingProperties.CONFIG_PREFIX)
public class VertexAiMultimodalEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.embedding.multimodal";

	private @Nullable String model;

	private @Nullable Integer outputDimensionality;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getOutputDimensionality() {
		return this.outputDimensionality;
	}

	public void setOutputDimensionality(@Nullable Integer outputDimensionality) {
		this.outputDimensionality = outputDimensionality;
	}

	public VertexAiMultimodalEmbeddingOptions toOptions() {
		VertexAiMultimodalEmbeddingOptions.Builder builder = VertexAiMultimodalEmbeddingOptions.builder();
		if (this.model != null) {
			builder.model(this.model);
		}
		if (this.outputDimensionality != null) {
			builder.dimensions(this.outputDimensionality);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.multimodal")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.multimodal.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return VertexAiMultimodalEmbeddingProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			VertexAiMultimodalEmbeddingProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.multimodal.output-dimensionality")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getOutputDimensionality() {
			return VertexAiMultimodalEmbeddingProperties.this.getOutputDimensionality();
		}

		public void setOutputDimensionality(@Nullable Integer outputDimensionality) {
			VertexAiMultimodalEmbeddingProperties.this.setOutputDimensionality(outputDimensionality);
		}

	}

}
