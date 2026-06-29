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

package org.springframework.ai.voyageai;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Options for the Voyage AI Embedding API.
 *
 * @author Spring AI
 * @since 2.0.0
 */
public class VoyageAiEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = VoyageAiEmbeddingModelName.VOYAGE_3_5.getName();

	/**
	 * ID of the model to use.
	 */
	private @Nullable String model;

	/**
	 * Type of the input text, either {@code query} or {@code document}. Used to tailor
	 * embeddings for retrieval/search tasks.
	 */
	private @Nullable String inputType;

	/**
	 * The number of dimensions for the resulting output embeddings.
	 */
	private @Nullable Integer outputDimension;

	/**
	 * Whether to truncate the input texts to fit within the context length.
	 */
	private @Nullable Boolean truncation;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getInputType() {
		return this.inputType;
	}

	public void setInputType(@Nullable String inputType) {
		this.inputType = inputType;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.outputDimension;
	}

	public @Nullable Integer getOutputDimension() {
		return this.outputDimension;
	}

	public void setOutputDimension(@Nullable Integer outputDimension) {
		this.outputDimension = outputDimension;
	}

	public @Nullable Boolean getTruncation() {
		return this.truncation;
	}

	public void setTruncation(@Nullable Boolean truncation) {
		this.truncation = truncation;
	}

	public static final class Builder {

		private final VoyageAiEmbeddingOptions options = new VoyageAiEmbeddingOptions();

		public Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder inputType(@Nullable String inputType) {
			this.options.setInputType(inputType);
			return this;
		}

		public Builder outputDimension(@Nullable Integer outputDimension) {
			this.options.setOutputDimension(outputDimension);
			return this;
		}

		public Builder truncation(@Nullable Boolean truncation) {
			this.options.setTruncation(truncation);
			return this;
		}

		public VoyageAiEmbeddingOptions build() {
			return this.options;
		}

	}

}
