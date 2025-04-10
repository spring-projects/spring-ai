package org.springframework.ai.mistralai.moderation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.moderation.ModerationOptions;

/**
 * @author Ricken Bazolo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MistralAiModerationOptions implements ModerationOptions {

	private static final String DEFAULT_MODEL = MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue();

	/**
	 * The model to use for moderation generation.
	 */
	@JsonProperty("model")
	private String model = DEFAULT_MODEL;

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

	public static final class Builder {

		private final MistralAiModerationOptions options;

		private Builder() {
			this.options = new MistralAiModerationOptions();
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public MistralAiModerationOptions build() {
			return this.options;
		}

	}

}
