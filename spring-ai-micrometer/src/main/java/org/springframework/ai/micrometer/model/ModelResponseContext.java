package org.springframework.ai.micrometer.model;

import org.springframework.lang.Nullable;

/**
 * Contextual data to observe in an model response.
 *
 * @author Thomas Vitale
 */
public record ModelResponseContext(
// @formatter:off
		@Nullable String finishReason,
		@Nullable String responseId,
		@Nullable String responseModel,
		@Nullable Integer completionTokens,
		@Nullable Integer promptTokens,
		@Nullable String completion
		// @formatter:on
) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String finishReason;

		private String responseId;

		private String responseModel;

		private Integer completionTokens;

		private Integer promptTokens;

		private String completion;

		private Builder() {
		}

		public Builder finishReasons(String finishReason) {
			this.finishReason = finishReason;
			return this;
		}

		public Builder responseId(String responseId) {
			this.responseId = responseId;
			return this;
		}

		public Builder responseModel(String responseModel) {
			this.responseModel = responseModel;
			return this;
		}

		public Builder completionTokens(Integer completionTokens) {
			this.completionTokens = completionTokens;
			return this;
		}

		public Builder promptTokens(Integer promptTokens) {
			this.promptTokens = promptTokens;
			return this;
		}

		public Builder completion(String completion) {
			this.completion = completion;
			return this;
		}

		public ModelResponseContext build() {
			return new ModelResponseContext(finishReason, responseId, responseModel, completionTokens, promptTokens,
					completion);
		}

	}

}
