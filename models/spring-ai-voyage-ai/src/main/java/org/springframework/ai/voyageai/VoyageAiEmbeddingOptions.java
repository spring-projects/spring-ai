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
import org.springframework.ai.voyageai.api.VoyageAiApi;

/**
 * Options for the Voyage AI text embedding models.
 *
 * <p>
 * There is no default model: the model has to be set, either here or on the request.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiEmbeddingOptions implements EmbeddingOptions {

	private final @Nullable String model;

	private final @Nullable Integer dimensions;

	private final VoyageAiApi.@Nullable InputType inputType;

	private final @Nullable Boolean truncation;

	protected VoyageAiEmbeddingOptions(@Nullable String model, @Nullable Integer dimensions,
			VoyageAiApi.@Nullable InputType inputType, @Nullable Boolean truncation) {
		this.model = model;
		this.dimensions = dimensions;
		this.inputType = inputType;
		this.truncation = truncation;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * The dimension of the vectors. Left unset, the model returns its own dimension. Only
	 * the Matryoshka models accept a value other than 1024.
	 * @return the output dimension, or {@code null} when none was requested
	 */
	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	/**
	 * Whether the texts are queries or documents.
	 * @return the input type, or {@code null} to send none
	 */
	public VoyageAiApi.@Nullable InputType getInputType() {
		return this.inputType;
	}

	/**
	 * Whether over-long texts are truncated rather than rejected.
	 * @return the truncation flag, or {@code null} to leave it to Voyage AI
	 */
	public @Nullable Boolean getTruncation() {
		return this.truncation;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable Integer dimensions;

		private VoyageAiApi.@Nullable InputType inputType;

		private @Nullable Boolean truncation;

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(VoyageAiApi.EmbeddingModel model) {
			this.model = model.getValue();
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder inputType(VoyageAiApi.@Nullable InputType inputType) {
			this.inputType = inputType;
			return this;
		}

		public Builder truncation(@Nullable Boolean truncation) {
			this.truncation = truncation;
			return this;
		}

		public VoyageAiEmbeddingOptions build() {
			return new VoyageAiEmbeddingOptions(this.model, this.dimensions, this.inputType, this.truncation);
		}

	}

}
