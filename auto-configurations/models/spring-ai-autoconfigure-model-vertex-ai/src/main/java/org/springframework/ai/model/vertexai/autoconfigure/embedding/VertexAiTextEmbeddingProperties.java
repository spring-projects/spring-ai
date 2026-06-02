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

import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiTextEmbeddingProperties.CONFIG_PREFIX)
public class VertexAiTextEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.embedding.text";

	private @Nullable String model;

	private VertexAiTextEmbeddingOptions.@Nullable TaskType taskType;

	private @Nullable Integer outputDimensionality;

	private @Nullable String title;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public VertexAiTextEmbeddingOptions.@Nullable TaskType getTaskType() {
		return this.taskType;
	}

	public void setTaskType(VertexAiTextEmbeddingOptions.@Nullable TaskType taskType) {
		this.taskType = taskType;
	}

	public @Nullable Integer getOutputDimensionality() {
		return this.outputDimensionality;
	}

	public void setOutputDimensionality(@Nullable Integer outputDimensionality) {
		this.outputDimensionality = outputDimensionality;
	}

	public @Nullable String getTitle() {
		return this.title;
	}

	public void setTitle(@Nullable String title) {
		this.title = title;
	}

	public VertexAiTextEmbeddingOptions toOptions() {
		return VertexAiTextEmbeddingOptions.builder()
			.model(this.model)
			.taskType(this.taskType)
			.dimensions(this.outputDimensionality)
			.title(this.title)
			.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.text")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.text.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return VertexAiTextEmbeddingProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			VertexAiTextEmbeddingProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.text.task-type")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public VertexAiTextEmbeddingOptions.@Nullable TaskType getTaskType() {
			return VertexAiTextEmbeddingProperties.this.getTaskType();
		}

		public void setTaskType(VertexAiTextEmbeddingOptions.@Nullable TaskType taskType) {
			VertexAiTextEmbeddingProperties.this.setTaskType(taskType);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.text.output-dimensionality")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getOutputDimensionality() {
			return VertexAiTextEmbeddingProperties.this.getOutputDimensionality();
		}

		public void setOutputDimensionality(@Nullable Integer outputDimensionality) {
			VertexAiTextEmbeddingProperties.this.setOutputDimensionality(outputDimensionality);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.vertex.ai.embedding.text.title")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getTitle() {
			return VertexAiTextEmbeddingProperties.this.getTitle();
		}

		public void setTitle(@Nullable String title) {
			VertexAiTextEmbeddingProperties.this.setTitle(title);
		}

	}

}
