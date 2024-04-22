package org.springframework.ai.zhipuai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author Ricken Bazolo
 */
@JsonInclude(Include.NON_NULL)
public class ZhipuAiEmbeddingOptions implements EmbeddingOptions {

	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;

	public static Builder builder() {
		return new Builder();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public static class Builder {

		protected ZhipuAiEmbeddingOptions options;

		public Builder() {
			this.options = new ZhipuAiEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public ZhipuAiEmbeddingOptions build() {
			return this.options;
		}

	}

}
