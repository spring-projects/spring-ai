/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.bedrock.cohere;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@JsonInclude(Include.NON_NULL)
public class BedrockCohereEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * Prepends special tokens to differentiate each type from one another. You should not mix
	 * different types together, except when mixing types for search and retrieval.
	 * In this case, embed your corpus with the search_document type and embedded queries with
	 * type search_query type.
	 */
	private @JsonProperty("input_type") InputType inputType;

	/**
	 * Specifies how the API handles inputs longer than the maximum token length. If you specify LEFT or
	 * RIGHT, the model discards the input until the remaining input is exactly the maximum input token length for the
	 * model.
	 */
	private @JsonProperty("truncate") Truncate truncate;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private BedrockCohereEmbeddingOptions options = new BedrockCohereEmbeddingOptions();

		public Builder withInputType(InputType inputType) {
			this.options.setInputType(inputType);
			return this;
		}

		public Builder withTruncate(Truncate truncate) {
			this.options.setTruncate(truncate);
			return this;
		}

		public BedrockCohereEmbeddingOptions build() {
			return this.options;
		}

	}

	public InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}

	public Truncate getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Truncate truncate) {
		this.truncate = truncate;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

}
