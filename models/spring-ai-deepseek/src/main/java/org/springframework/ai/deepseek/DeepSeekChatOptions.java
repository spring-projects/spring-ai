/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Chat completions options for the DeepSeek chat API.
 * <a href="https://platform.deepseek.com/api-docs/api/create-chat-completion">DeepSeek
 * chat completion</a>
 *
 * @author Geng Rong
 */
@JsonInclude(Include.NON_NULL)
public class DeepSeekChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
	 */
	private @JsonProperty("model") String model;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 */
	private @JsonProperty("frequency_penalty") Float frequencyPenalty;
	/**
	 * The maximum number of tokens that can be generated in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 */
	private @JsonProperty("presence_penalty") Float presencePenalty;
	/**
	 * A string or a list containing up to 4 strings, upon encountering these words, the API will cease generating more tokens.
	 */
	@NestedConfigurationProperty
	private @JsonProperty("stop") List<String> stop;
	/**
	 * What sampling temperature to use, between 0 and 2.
	 * Higher values like 0.8 will make the output more random,
	 * while lower values like 0.2 will make it more focused and deterministic.
	 * We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Float temperature;
	/**
	 * An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass.
	 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 */
	private @JsonProperty("top_p") Float topP;
	/**
	 * Whether to return log probabilities of the output tokens or not.
	 * If true, returns the log probabilities of each output token returned in the content of message.
	 */
	private @JsonProperty("logprobs") Boolean logprobs;
	/**
	 * An integer between 0 and 20 specifying the number of most likely tokens to return at each token position,
	 * each with an associated log probability. logprobs must be set to true if this parameter is used.
	 */
	private @JsonProperty("top_logprobs") Integer topLogprobs;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected DeepSeekChatOptions options;

		public Builder() {
			this.options = new DeepSeekChatOptions();
		}

		public Builder(DeepSeekChatOptions options) {
			this.options = options;
		}

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withFrequencyPenalty(Float frequencyPenalty) {
			this.options.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder withLogprobs(Boolean logprobs) {
			this.options.logprobs = logprobs;
			return this;
		}

		public Builder withTopLogprobs(Integer topLogprobs) {
			this.options.topLogprobs = topLogprobs;
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder withPresencePenalty(Float presencePenalty) {
			this.options.presencePenalty = presencePenalty;
			return this;
		}

		public Builder withStop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public DeepSeekChatOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((frequencyPenalty == null) ? 0 : frequencyPenalty.hashCode());
		result = prime * result + ((logprobs == null) ? 0 : logprobs.hashCode());
		result = prime * result + ((topLogprobs == null) ? 0 : topLogprobs.hashCode());
		result = prime * result + ((maxTokens == null) ? 0 : maxTokens.hashCode());
		result = prime * result + ((presencePenalty == null) ? 0 : presencePenalty.hashCode());
		result = prime * result + ((stop == null) ? 0 : stop.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DeepSeekChatOptions other = (DeepSeekChatOptions) obj;
		if (this.model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (this.frequencyPenalty == null) {
			if (other.frequencyPenalty != null)
				return false;
		}
		else if (!this.frequencyPenalty.equals(other.frequencyPenalty))
			return false;
		if (this.logprobs == null) {
			if (other.logprobs != null)
				return false;
		}
		else if (!this.logprobs.equals(other.logprobs))
			return false;
		if (this.topLogprobs == null) {
			if (other.topLogprobs != null)
				return false;
		}
		else if (!this.topLogprobs.equals(other.topLogprobs))
			return false;
		if (this.maxTokens == null) {
			if (other.maxTokens != null)
				return false;
		}
		else if (!this.maxTokens.equals(other.maxTokens))
			return false;
		if (this.presencePenalty == null) {
			if (other.presencePenalty != null)
				return false;
		}
		else if (!this.presencePenalty.equals(other.presencePenalty))
			return false;
		if (this.stop == null) {
			if (other.stop != null)
				return false;
		}
		else if (!stop.equals(other.stop))
			return false;
		if (this.temperature == null) {
			if (other.temperature != null)
				return false;
		}
		else if (!this.temperature.equals(other.temperature))
			return false;
		if (this.topP == null) {
			if (other.topP != null)
				return false;
		}
		else if (!topP.equals(other.topP))
			return false;
		return true;
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

	public static DeepSeekChatOptions fromOptions(DeepSeekChatOptions fromOptions) {
		return builder().withModel(fromOptions.getModel())
			.withFrequencyPenalty(fromOptions.getFrequencyPenalty())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withPresencePenalty(fromOptions.getPresencePenalty())
			.withStop(fromOptions.getStop())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withLogprobs(fromOptions.getLogprobs())
			.withTopLogprobs(fromOptions.getTopLogprobs())
			.build();
	}

}
