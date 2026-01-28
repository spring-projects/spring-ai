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
	 * Whether to normalize the embedding vectors.
	 */
	@JsonProperty("normalize")
	private Boolean normalize;

	/**
	 * The name of a predefined prompt from the model configuration to apply to the input
	 * text.
	 * <p>
	 * For example, setting this to "query" might prepend "query: " to your input text,
	 * which can improve retrieval performance for query-document matching tasks.
	 */
	@JsonProperty("prompt_name")
	private String promptName;

	/**
	 * Whether to truncate input text that exceeds the model's maximum sequence length.
	 */
	@JsonProperty("truncate")
	private Boolean truncate;

	/**
	 * Which side of the text to truncate when it exceeds the maximum length. Must be
	 * either "left" or "right".
	 * <p>
	 * Only meaningful when truncate is set to true.
	 */
	@JsonProperty("truncation_direction")
	private String truncationDirection;

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
			.normalize(fromOptions.getNormalize())
			.promptName(fromOptions.getPromptName())
			.truncate(fromOptions.getTruncate())
			.truncationDirection(fromOptions.getTruncationDirection())
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
		return null;
	}

	public Boolean getNormalize() {
		return this.normalize;
	}

	public void setNormalize(Boolean normalize) {
		this.normalize = normalize;
	}

	public String getPromptName() {
		return this.promptName;
	}

	public void setPromptName(String promptName) {
		this.promptName = promptName;
	}

	public Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Boolean truncate) {
		this.truncate = truncate;
	}

	public String getTruncationDirection() {
		return this.truncationDirection;
	}

	public void setTruncationDirection(String truncationDirection) {
		this.truncationDirection = truncationDirection;
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
		return Objects.equals(this.model, that.model) && Objects.equals(this.normalize, that.normalize)
				&& Objects.equals(this.promptName, that.promptName) && Objects.equals(this.truncate, that.truncate)
				&& Objects.equals(this.truncationDirection, that.truncationDirection);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.normalize, this.promptName, this.truncate, this.truncationDirection);
	}

	@Override
	public String toString() {
		return "HuggingfaceEmbeddingOptions{" + "model='" + this.model + '\'' + ", normalize=" + this.normalize
				+ ", promptName='" + this.promptName + '\'' + ", truncate=" + this.truncate + ", truncationDirection='"
				+ this.truncationDirection + '\'' + '}';
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
		 * Set whether to normalize the embedding vectors.
		 * @param normalize True to normalize, false otherwise.
		 * @return This builder.
		 */
		public Builder normalize(Boolean normalize) {
			this.options.normalize = normalize;
			return this;
		}

		/**
		 * Set the name of a predefined prompt to apply to the input text.
		 * @param promptName The prompt name from the model configuration.
		 * @return This builder.
		 */
		public Builder promptName(String promptName) {
			this.options.promptName = promptName;
			return this;
		}

		/**
		 * Set whether to truncate input text that exceeds the model's maximum length.
		 * @param truncate True to truncate, false otherwise.
		 * @return This builder.
		 */
		public Builder truncate(Boolean truncate) {
			this.options.truncate = truncate;
			return this;
		}

		/**
		 * Set which side of the text to truncate when it exceeds the maximum length.
		 * @param truncationDirection Either "left" or "right" (case-sensitive).
		 * @return This builder.
		 */
		public Builder truncationDirection(String truncationDirection) {
			this.options.truncationDirection = truncationDirection;
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
