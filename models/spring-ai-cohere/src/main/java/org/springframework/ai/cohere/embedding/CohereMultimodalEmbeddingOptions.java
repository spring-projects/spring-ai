/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.cohere.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingRequest.InputType;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingRequest.Truncate;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingType;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Options for the Cohere Multimodal Embedding API.
 *
 * @author Ricken Bazolo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CohereMultimodalEmbeddingOptions implements EmbeddingOptions {

	@JsonProperty("model")
	private String model;

	@JsonProperty("input_type")
	private InputType inputType;

	@JsonProperty("embedding_types")
	private List<EmbeddingType> embeddingTypes = new ArrayList<>();

	@JsonProperty("truncate")
	private Truncate truncate;

	public static Builder builder() {
		return new Builder();
	}

	public static CohereMultimodalEmbeddingOptions fromOptions(CohereMultimodalEmbeddingOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.inputType(fromOptions.getInputType())
			.embeddingTypes(
					fromOptions.getEmbeddingTypes() != null ? new ArrayList<>(fromOptions.getEmbeddingTypes()) : null)
			.truncate(fromOptions.getTruncate())
			.build();
	}

	private CohereMultimodalEmbeddingOptions() {
		this.embeddingTypes.add(EmbeddingType.FLOAT);
		this.inputType = InputType.CLASSIFICATION;
		this.model = CohereApi.EmbeddingModel.EMBED_V4.getValue();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}

	public List<EmbeddingType> getEmbeddingTypes() {
		return this.embeddingTypes;
	}

	public void setEmbeddingTypes(List<EmbeddingType> embeddingTypes) {
		this.embeddingTypes = embeddingTypes;
	}

	public Truncate getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Truncate truncate) {
		this.truncate = truncate;
	}

	@Override
	public Integer getDimensions() {
		return null;
	}

	public CohereMultimodalEmbeddingOptions copy() {
		return fromOptions(this);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.inputType, this.embeddingTypes, this.truncate);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		CohereMultimodalEmbeddingOptions that = (CohereMultimodalEmbeddingOptions) o;

		return Objects.equals(this.model, that.model) && Objects.equals(this.inputType, that.inputType)
				&& Objects.equals(this.embeddingTypes, that.embeddingTypes)
				&& Objects.equals(this.truncate, that.truncate);
	}

	public static final class Builder {

		private CohereMultimodalEmbeddingOptions options;

		public Builder() {
			this.options = new CohereMultimodalEmbeddingOptions();
		}

		public Builder(CohereMultimodalEmbeddingOptions options) {
			this.options = options;
		}

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(CohereApi.EmbeddingModel model) {
			this.options.model = model.getValue();
			return this;
		}

		public Builder inputType(InputType inputType) {
			this.options.inputType = inputType;
			return this;
		}

		public Builder embeddingTypes(List<EmbeddingType> embeddingTypes) {
			this.options.embeddingTypes = embeddingTypes;
			return this;
		}

		public Builder truncate(Truncate truncate) {
			this.options.truncate = truncate;
			return this;
		}

		public CohereMultimodalEmbeddingOptions build() {
			return this.options;
		}

	}

}
