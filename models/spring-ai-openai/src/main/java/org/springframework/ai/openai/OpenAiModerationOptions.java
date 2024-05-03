package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Ricken Bazolo
 */
@JsonInclude(Include.NON_NULL)
public class OpenAiModerationOptions implements ChatOptions {

	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected OpenAiModerationOptions options;

		public Builder() {
			this.options = new OpenAiModerationOptions();
		}

		public Builder(OpenAiModerationOptions options) {
			this.options = options;
		}

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public OpenAiModerationOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Float getTemperature() {
		throw new UnsupportedOperationException("Unimplemented method 'getTemperature'");
	}

	@Override
	public Float getTopP() {
		throw new UnsupportedOperationException("Unimplemented method 'getTopP'");
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		throw new UnsupportedOperationException("Unimplemented method 'getTopK'");
	}

	@JsonIgnore
	public void setTopK(Integer topK) {
		throw new UnsupportedOperationException("Unimplemented method 'setTopK'");
	}

}
