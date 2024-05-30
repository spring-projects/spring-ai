package org.springframework.ai.wenxin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:28
 * @description:
 */
public class WenxinEmbeddingOptions implements EmbeddingOptions {

	private @JsonProperty("model") String model;

	private @JsonProperty("user_id") String userId;

	public static Builder builder() {
		return new Builder();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public static class Builder {

		protected WenxinEmbeddingOptions options;

		public Builder() {
			this.options = new WenxinEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withUserId(String userId) {
			this.options.setUserId(userId);
			return this;
		}

		public WenxinEmbeddingOptions build() {
			return this.options;
		}

	}

}
