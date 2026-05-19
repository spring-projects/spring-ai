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

package org.springframework.ai.bedrock.cohere;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Options for the Bedrock Cohere embedding API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public class BedrockCohereEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * Prepends special tokens to differentiate each type from one another. You should not mix
	 * different types together, except when mixing types for search and retrieval.
	 * In this case, embed your corpus with the search_document type and embedded queries with
	 * type search_query type.
	 */
	private @Nullable InputType inputType;

	/**
	 * Specifies how the API handles inputs longer than the maximum token length. If you specify LEFT or
	 * RIGHT, the model discards the input until the remaining input is exactly the maximum input token length for the
	 * model.
	 */
	private @Nullable Truncate truncate;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(@Nullable InputType inputType) {
		this.inputType = inputType;
	}

	public @Nullable Truncate getTruncate() {
		return this.truncate;
	}

	public void setTruncate(@Nullable Truncate truncate) {
		this.truncate = truncate;
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

		private BedrockCohereEmbeddingOptions options = new BedrockCohereEmbeddingOptions();

		public Builder inputType(@Nullable InputType inputType) {
			this.options.setInputType(inputType);
			return this;
		}

		public Builder truncate(@Nullable Truncate truncate) {
			this.options.setTruncate(truncate);
			return this;
		}

		public BedrockCohereEmbeddingOptions build() {
			return this.options;
		}

	}

}
