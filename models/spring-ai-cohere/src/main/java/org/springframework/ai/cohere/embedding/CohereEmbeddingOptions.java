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
 * Options for the Cohere Embedding API.
 *
 * @author Ricken Bazolo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CohereEmbeddingOptions implements EmbeddingOptions {

	/**
	 * ID of the model to use.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The type of input (search_document, search_query, classification, clustering).
	 */
	@JsonProperty("input_type")
	private InputType inputType;

	/**
	 * The types of embeddings to return (float, int8, uint8, binary, ubinary).
	 */
	@JsonProperty("embedding_types")
	private List<EmbeddingType> embeddingTypes = new ArrayList<>();

	/**
	 * How to handle inputs longer than the maximum token length (NONE, START, END).
	 */
	@JsonProperty("truncate")
	private Truncate truncate;

	public static Builder builder() {
		return new Builder();
	}

	public static CohereEmbeddingOptions fromOptions(CohereEmbeddingOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.inputType(fromOptions.getInputType())
			.embeddingTypes(
					fromOptions.getEmbeddingTypes() != null ? new ArrayList<>(fromOptions.getEmbeddingTypes()) : null)
			.truncate(fromOptions.getTruncate())
			.build();
	}

	private CohereEmbeddingOptions() {
		this.embeddingTypes.add(EmbeddingType.FLOAT);
		this.inputType = InputType.CLASSIFICATION;
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
		// Cohere embeddings have fixed dimensions based on model
		// embed-multilingual-v3 and embed-english-v3: 1024
		// This should be handled by the model implementation
		return null;
	}

	public CohereEmbeddingOptions copy() {
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

		CohereEmbeddingOptions that = (CohereEmbeddingOptions) o;

		return Objects.equals(this.model, that.model) && Objects.equals(this.inputType, that.inputType)
				&& Objects.equals(this.embeddingTypes, that.embeddingTypes)
				&& Objects.equals(this.truncate, that.truncate);
	}

	public static final class Builder {

		private CohereEmbeddingOptions options;

		public Builder() {
			this.options = new CohereEmbeddingOptions();
		}

		public Builder(CohereEmbeddingOptions options) {
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

		public CohereEmbeddingOptions build() {
			return this.options;
		}

	}

}
