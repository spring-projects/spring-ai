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

package org.springframework.ai.huggingface;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptionsUtils;

/**
 * Options for HuggingFace embedding model.
 *
 * @author Myeongdeok Kang
 */
@JsonInclude(Include.NON_NULL)
public class HuggingfaceEmbeddingOptions implements EmbeddingOptions {

	/**
	 * The name of the model to use for embeddings.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The number of dimensions for the embedding vectors. Note: Not all HuggingFace
	 * models support this option. If specified but not supported by the model, it will be
	 * ignored.
	 */
	@JsonProperty("dimensions")
	private Integer dimensions;

	/**
	 * Whether to normalize the embedding vectors.
	 */
	@JsonProperty("normalize")
	private Boolean normalize;

	/**
	 * Create a new builder for HuggingfaceEmbeddingOptions.
	 * @return A new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a copy from existing options.
	 * @param fromOptions The options to copy from.
	 * @return A new HuggingfaceEmbeddingOptions instance with copied values.
	 */
	public static HuggingfaceEmbeddingOptions fromOptions(HuggingfaceEmbeddingOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.dimensions(fromOptions.getDimensions())
			.normalize(fromOptions.getNormalize())
			.build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public Boolean getNormalize() {
		return this.normalize;
	}

	public void setNormalize(Boolean normalize) {
		this.normalize = normalize;
	}

	/**
	 * Create a copy of this options instance.
	 * @return A new copy of this options.
	 */
	public HuggingfaceEmbeddingOptions copy() {
		return fromOptions(this);
	}

	/**
	 * Convert the {@link HuggingfaceEmbeddingOptions} object to a {@link Map} of
	 * key/value pairs.
	 * @return the {@link Map} of key/value pairs
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		HuggingfaceEmbeddingOptions that = (HuggingfaceEmbeddingOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.dimensions, that.dimensions)
				&& Objects.equals(this.normalize, that.normalize);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.dimensions, this.normalize);
	}

	@Override
	public String toString() {
		return "HuggingfaceEmbeddingOptions{" + "model='" + this.model + '\'' + ", dimensions=" + this.dimensions
				+ ", normalize=" + this.normalize + '}';
	}

	/**
	 * Builder for HuggingfaceEmbeddingOptions.
	 */
	public static final class Builder {

		private final HuggingfaceEmbeddingOptions options = new HuggingfaceEmbeddingOptions();

		private Builder() {
		}

		/**
		 * Set the model name.
		 * @param model The model name.
		 * @return This builder.
		 */
		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		/**
		 * Set the number of dimensions for embedding vectors.
		 * @param dimensions The number of dimensions.
		 * @return This builder.
		 */
		public Builder dimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		/**
		 * Set whether to normalize the embedding vectors.
		 * @param normalize True to normalize, false otherwise.
		 * @return This builder.
		 */
		public Builder normalize(Boolean normalize) {
			this.options.normalize = normalize;
			return this;
		}

		/**
		 * Build the HuggingfaceEmbeddingOptions instance.
		 * @return A new HuggingfaceEmbeddingOptions instance.
		 */
		public HuggingfaceEmbeddingOptions build() {
			return this.options;
		}

	}

}
