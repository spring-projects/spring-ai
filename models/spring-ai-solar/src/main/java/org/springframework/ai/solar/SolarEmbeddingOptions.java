package org.springframework.ai.solar;

import org.springframework.ai.embedding.EmbeddingOptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class represents the options for Solar embedding.
 *
 * @author Seunghyeon Ji
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolarEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;

	public static Builder builder() {
		return new Builder();
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

	public static class Builder {

		protected SolarEmbeddingOptions options;

		public Builder() {
			this.options = new SolarEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public SolarEmbeddingOptions build() {
			return this.options;
		}
	}
}

