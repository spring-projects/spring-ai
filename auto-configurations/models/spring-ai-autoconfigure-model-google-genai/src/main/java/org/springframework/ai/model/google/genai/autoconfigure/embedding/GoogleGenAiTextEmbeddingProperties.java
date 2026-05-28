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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google GenAI Text Embedding.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiTextEmbeddingProperties.CONFIG_PREFIX)
public class GoogleGenAiTextEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.embedding.text";

	/**
	 * Google GenAI Text Embedding API options.
	 */
	private final Options options = new Options();

	public Options getOptions() {
		return this.options;
	}

	public static class Options {

		private @Nullable String model;

		private GoogleGenAiTextEmbeddingOptions.@Nullable TaskType taskType;

		private @Nullable Integer dimensions;

		private @Nullable String title;

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
		}

		public GoogleGenAiTextEmbeddingOptions.@Nullable TaskType getTaskType() {
			return this.taskType;
		}

		public void setTaskType(GoogleGenAiTextEmbeddingOptions.@Nullable TaskType taskType) {
			this.taskType = taskType;
		}

		public @Nullable Integer getDimensions() {
			return this.dimensions;
		}

		public void setDimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
		}

		public @Nullable String getTitle() {
			return this.title;
		}

		public void setTitle(@Nullable String title) {
			this.title = title;
		}

		public GoogleGenAiTextEmbeddingOptions toOptions() {
			return GoogleGenAiTextEmbeddingOptions.builder()
				.model(this.model)
				.taskType(this.taskType)
				.dimensions(this.dimensions)
				.title(this.title)
				.build();
		}

	}

}
