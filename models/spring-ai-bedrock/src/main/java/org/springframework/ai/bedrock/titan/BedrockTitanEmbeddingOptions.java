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

package org.springframework.ai.bedrock.titan;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel.InputType;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.Assert;

/**
 * Options for the Titan Embedding API.
 *
 * @author Wei Jiang
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 */
public class BedrockTitanEmbeddingOptions implements EmbeddingOptions {

	/**
	 * Titan Embedding API input types. Could be either text or image (encoded in base64).
	 */
	private final @Nullable InputType inputType;

	protected BedrockTitanEmbeddingOptions(@Nullable InputType inputType) {
		this.inputType = inputType;
	}

	public static BedrockTitanEmbeddingOptions.Builder builder() {
		return new Builder();
	}

	public @Nullable InputType getInputType() {
		return this.inputType;
	}

	@Override
	public @Nullable String getModel() {
		return null;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return null;
	}

	public static final class Builder {

		private @Nullable InputType inputType;

		public Builder inputType(InputType inputType) {
			Assert.notNull(inputType, "input type can not be null.");

			this.inputType = inputType;
			return this;
		}

		public BedrockTitanEmbeddingOptions build() {
			return new BedrockTitanEmbeddingOptions(this.inputType);
		}

	}

}
