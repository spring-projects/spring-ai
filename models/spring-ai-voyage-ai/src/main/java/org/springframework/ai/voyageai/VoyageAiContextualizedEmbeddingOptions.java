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
 * Options for the Voyage AI contextualized embedding models.
 *
 * <p>
 * There is no default model, and the input type has to be set: Voyage AI chunks and
 * embeds {@link VoyageAiApi.InputType#DOCUMENT documents}, and embeds
 * {@link VoyageAiApi.InputType#QUERY queries} as they are.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiContextualizedEmbeddingOptions implements EmbeddingOptions {

	private final @Nullable String model;

	private final @Nullable Integer dimensions;

	private final VoyageAiApi.@Nullable InputType inputType;

	protected VoyageAiContextualizedEmbeddingOptions(@Nullable String model, @Nullable Integer dimensions,
			VoyageAiApi.@Nullable InputType inputType) {
		this.model = model;
		this.dimensions = dimensions;
		this.inputType = inputType;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * The dimension of the vectors: 256, 512, 1024 or 2048. Left unset, the model returns
	 * 1024.
	 * @return the output dimension, or {@code null} when none was requested
	 */
	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	/**
	 * Whether the inputs are documents or queries.
	 * @return the input type, {@code null} when it was not set
	 */
	public VoyageAiApi.@Nullable InputType getInputType() {
		return this.inputType;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable Integer dimensions;

		private VoyageAiApi.@Nullable InputType inputType;

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(VoyageAiApi.ContextualizedEmbeddingModel model) {
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

		public VoyageAiContextualizedEmbeddingOptions build() {
			return new VoyageAiContextualizedEmbeddingOptions(this.model, this.dimensions, this.inputType);
		}

	}

}
